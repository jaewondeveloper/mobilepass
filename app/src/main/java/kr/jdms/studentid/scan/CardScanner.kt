package kr.jdms.studentid.scan

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kr.jdms.studentid.R
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 학생증 스캐너 (화면 없음). 미리보기는 호출한 쪽이 넘겨주는 [PreviewView] 에 그린다.
 *
 * 미리보기 영역 = 가이드 카드 영역이므로, 분석 이미지는 곧바로 '카드 틀 안쪽'이다.
 * 프레임마다 ML Kit 바코드 + 한글 OCR 을 **병렬**로 돌리고, [ScanConsensus] 로 확정한다.
 * (정지 촬영·재분석 없이 분석 프레임 그대로 확정 → 빠름)
 */
class CardScanner(
    private val activity: AppCompatActivity,
    private val previewView: PreviewView,
    private val listener: Listener,
) {
    interface Listener {
        /** 안내 문구/카드 감지 상태가 바뀔 때만 호출 (메인 스레드) */
        fun onState(hint: String, detected: Boolean)

        /** 확정된 결과 (메인 스레드). thumbJpeg 는 확인 화면용 작은 JPEG */
        fun onResult(name: String, code: String, thumbJpeg: ByteArray)

        fun onError(message: String)
    }

    private enum class Phase { IDLE, SCANNING, DONE }

    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val barcodeScanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39, Barcode.FORMAT_ITF)
                .build(),
        )
    }
    private val textRecognizer by lazy { TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()) }

    private val consensus = ScanConsensus()
    @Volatile private var phase = Phase.IDLE
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var camera: androidx.camera.core.Camera? = null
    private var lastHint = ""
    private var lastDetected = false
    private var lastProgressAt = 0L
    private var released = false

    /** 모델을 미리 올려 두어 첫 스캔의 대기 시간을 줄인다. (앱 시작 시 한 번) */
    fun prewarm() {
        worker.execute {
            try {
                val blank = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                val img = InputImage.fromBitmap(blank, 0)
                Tasks.await(Tasks.whenAllComplete(barcodeScanner.process(img), textRecognizer.process(img)))
                blank.recycle()
            } catch (e: Exception) {
                Log.w(TAG, "prewarm", e)
            }
        }
        ProcessCameraProvider.getInstance(activity)   // 카메라 서비스 연결도 미리
    }

    fun start() {
        if (released) return
        val future = ProcessCameraProvider.getInstance(activity)
        future.addListener({
            try {
                provider = future.get()
                bind()
            } catch (e: Exception) {
                Log.e(TAG, "camera provider", e)
                listener.onError(activity.getString(R.string.err_camera))
            }
        }, ContextCompat.getMainExecutor(activity))
    }

    /** 미리보기 크기가 바뀌었거나 다시 시작할 때 */
    fun rebind() {
        if (provider != null) bind()
    }

    /** 확인 화면에서 '다시 스캔'을 눌렀을 때 */
    fun resume() {
        if (released) return
        consensus.reset()
        lastHint = ""
        lastProgressAt = SystemClock.elapsedRealtime()
        phase = Phase.SCANNING
        analysis?.setAnalyzer(worker) { proxy -> analyzeFrame(proxy) }
    }

    fun stop() {
        phase = Phase.IDLE
        analysis?.clearAnalyzer()
        analysis = null
        camera = null
        provider?.unbindAll()
    }

    fun release() {
        released = true
        stop()
        worker.shutdownNow()
        runCatching { barcodeScanner.close() }
        runCatching { textRecognizer.close() }
    }

    fun focusAt(x: Float, y: Float) {
        val p = previewView.meteringPointFactory.createPoint(x, y)
        camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(p).build())
    }

    private fun bind() {
        val provider = provider ?: return
        val viewPort = previewView.viewPort
        if (viewPort == null) {
            // 아직 레이아웃 전: 크기가 정해진 뒤에 다시 시도
            previewView.doOnLayout {
                if (released) return@doOnLayout
                if (previewView.viewPort != null) bind() else listener.onError(activity.getString(R.string.err_camera))
            }
            return
        }
        val ratio43 = AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO)
        val preview = Preview.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio43).build())
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }

        // 카드 틀 안쪽만 분석: 1440x1080 급이면 이름 같은 작은 글씨도 읽히면서 빠르다
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(ratio43)
                    .setResolutionStrategy(ResolutionStrategy(Size(1440, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build(),
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        val group = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(analysis)
            .setViewPort(viewPort)
            .build()
        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(activity, CameraSelector.DEFAULT_BACK_CAMERA, group)
        } catch (e: Exception) {
            Log.e(TAG, "bind", e)
            listener.onError(activity.getString(R.string.err_camera))
            return
        }
        this.analysis = analysis
        resume()
    }

    // ───────────── 프레임 분석 (백그라운드) ─────────────

    private fun analyzeFrame(proxy: ImageProxy) {
        try {
            if (phase != Phase.SCANNING) return
            val card = proxy.toUprightBitmap()      // 미리보기에 보이는 영역 = 카드 틀 안쪽
            val luma = card.meanLuma()
            val obs = recognize(card)
            val result = synchronized(consensus) {
                consensus.add(obs)
                consensus.result()
            }
            if (result != null && phase == Phase.SCANNING) {
                phase = Phase.DONE
                val jpeg = thumbnail(card)
                card.recycle()
                activity.runOnUiThread { if (!released) listener.onResult(result.name, result.code, jpeg) }
            } else {
                card.recycle()
                val hint = hintFor(obs, luma)
                val detected = obs.hasSchool || obs.barcode != null
                activity.runOnUiThread { emitState(hint, detected) }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "analyze", t)
        } finally {
            proxy.close()
        }
    }

    /** 바코드와 OCR 을 동시에 실행해서 둘 중 느린 쪽 시간만큼만 걸리게 한다. */
    private fun recognize(bitmap: Bitmap): Observation {
        val image = InputImage.fromBitmap(bitmap, 0)
        val barcodeTask = barcodeScanner.process(image)
        val textTask = textRecognizer.process(image)
        try {
            Tasks.await(Tasks.whenAllComplete(barcodeTask, textTask))
        } catch (e: Exception) {
            Log.w(TAG, "recognize", e)
        }

        val barcode = if (barcodeTask.isSuccessful) {
            barcodeTask.result.asSequence().mapNotNull { CardParser.normalizeCode(it.rawValue) }.firstOrNull()
        } else null

        val lines = if (textTask.isSuccessful) {
            textTask.result.textBlocks.flatMap { it.lines }.map { l ->
                val b = l.boundingBox
                if (b != null) OcrLine(l.text, b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
                else OcrLine(l.text)
            }
        } else emptyList()

        val parsed = CardParser.parse(lines)
        return Observation(barcode, parsed.ocrCode, parsed.name, parsed.hasSchool, parsed.hasTitle)
    }

    private fun thumbnail(card: Bitmap): ByteArray {
        val longest = maxOf(card.width, card.height)
        val s = if (longest > THUMB_MAX) THUMB_MAX.toFloat() / longest else 1f
        val small = if (s < 1f) Bitmap.createScaledBitmap(card, (card.width * s).toInt(), (card.height * s).toInt(), true) else card
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 80, out)
        if (small !== card) small.recycle()
        return out.toByteArray()
    }

    private fun hintFor(obs: Observation, luma: Float): String {
        val now = SystemClock.elapsedRealtime()
        val school: Boolean
        val code: Boolean
        val name: Boolean
        synchronized(consensus) {
            school = consensus.schoolSeen
            code = consensus.leadingCode() != null
            name = consensus.leadingName() != null
        }
        if (obs.hasSchool || obs.barcode != null || obs.name != null) lastProgressAt = now
        return activity.getString(
            when {
                luma < LUMA_DARK -> R.string.hint_dark
                luma > LUMA_GLARE -> R.string.hint_glare
                school && code && !name -> R.string.hint_need_name
                school && code -> R.string.hint_reading
                school -> R.string.hint_need_barcode
                code -> R.string.hint_need_school
                now - lastProgressAt > LOST_MS -> R.string.hint_lost
                else -> R.string.hint_default
            },
        )
    }

    private fun emitState(hint: String, detected: Boolean) {
        if (released || phase != Phase.SCANNING) return
        if (hint == lastHint && detected == lastDetected) return
        lastHint = hint
        lastDetected = detected
        listener.onState(hint, detected)
    }

    private companion object {
        const val TAG = "CardScanner"
        const val THUMB_MAX = 520
        const val LUMA_DARK = 35f
        const val LUMA_GLARE = 235f
        const val LOST_MS = 10_000L
    }
}
