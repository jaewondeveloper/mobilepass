package kr.jdms.studentid.face

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
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
import com.google.mlkit.vision.common.InputImage
import kr.jdms.studentid.R
import kr.jdms.studentid.scan.toUprightBitmap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * 얼굴 두 단계를 맡는 스캐너 (화면 없음, 미리보기는 호출한 쪽의 PreviewView).
 *
 *  - CARD   : 후면 카메라로 학생증 앞면의 증명사진을 찾아 '정렬·보정'한 뒤 임베딩을 잠시 메모리에 보관
 *  - SELFIE : 전면 카메라로 본인 확인 — 정면·눈 뜸·선명한 프레임을 모으고, 눈 깜빡임(실물 확인)을 거쳐
 *             증명사진과 비교하고, 통과하면 프로필 사진(배경 제거/보정/각도 보정)을 만든다
 */
class FaceScanner(
    private val activity: AppCompatActivity,
    private val previewView: PreviewView,
    private val embedder: FaceEmbedder,
    private val listener: Listener,
) {
    enum class Mode { CARD, SELFIE }

    /** matched/score 가 null 이면 얼굴 비교 모델이 없어 비교를 건너뛴 것 */
    class SelfieResult(val matched: Boolean?, val score: Float?, val png: ByteArray?)

    interface Listener {
        fun onState(hint: String, detected: Boolean)
        fun onCardFace()
        fun onSelfie(result: SelfieResult)
        fun onError(message: String)
    }

    private enum class Phase { IDLE, RUNNING, DONE }

    private class CardSample(val aligned: Bitmap, val sharp: Double)
    private class GoodFrame(val frame: Bitmap, val face: FaceInfo, val score: Double)

    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val cardDetector by lazy { makeFaceDetector(fast = false, minFace = 0.05f, classification = false) }
    private val selfieDetector by lazy { makeFaceDetector(fast = true, minFace = 0.25f, classification = true) }
    private val portrait = PortraitProcessor()

    @Volatile private var phase = Phase.IDLE
    private var mode = Mode.SELFIE
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var released = false
    private var lastHint = ""
    private var lastDetected = false

    // CARD 상태
    private val cardSamples = ArrayList<CardSample>()
    // SELFIE 상태
    private val goods = ArrayList<GoodFrame>()
    private val blink = BlinkDetector()

    fun start(mode: Mode) {
        if (released) return
        this.mode = mode
        resetUiState()
        worker.execute { resetState() }
        val future = ProcessCameraProvider.getInstance(activity)
        future.addListener({
            try {
                provider = future.get()
                previewView.doOnLayout { bind() }
            } catch (e: Exception) {
                Log.e(TAG, "camera provider", e)
                listener.onError(activity.getString(R.string.err_camera))
            }
        }, ContextCompat.getMainExecutor(activity))
    }

    fun stop() {
        phase = Phase.IDLE
        analysis?.clearAnalyzer()
        analysis = null
        provider?.unbindAll()
        resetUiState()
        if (!worker.isShutdown) worker.execute { resetState() }     // 분석 스레드가 쓰는 목록은 그 스레드에서 정리
    }

    fun release() {
        released = true
        stop()
        worker.shutdownNow()
        runCatching { cardDetector.close() }
        runCatching { selfieDetector.close() }
        portrait.close()
    }

    /** 분석 스레드에서만 호출 */
    private fun resetState() {
        cardSamples.forEach { it.aligned.recycle() }
        cardSamples.clear()
        goods.forEach { it.frame.recycle() }
        goods.clear()
        blink.reset()
    }

    /** 메인 스레드에서만 호출 */
    private fun resetUiState() {
        lastHint = ""
        lastDetected = false
    }

    private fun bind() {
        val provider = provider ?: return
        val viewPort = previewView.viewPort
        if (viewPort == null) {
            listener.onError(activity.getString(R.string.err_camera))
            return
        }
        val ratio43 = AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO)
        val preview = Preview.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio43).build())
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }
        // 학생증 속 작은 얼굴은 해상도가 필요하고, 셀피는 얼굴이 크므로 낮춰도 된다
        val size = if (mode == Mode.CARD) Size(1920, 1440) else Size(1280, 960)
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(ratio43)
                    .setResolutionStrategy(ResolutionStrategy(size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build(),
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        val group = UseCaseGroup.Builder().addUseCase(preview).addUseCase(analysis).setViewPort(viewPort).build()
        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                activity,
                if (mode == Mode.CARD) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA,
                group,
            )
        } catch (e: Exception) {
            Log.e(TAG, "bind", e)
            listener.onError(activity.getString(R.string.err_camera))
            return
        }
        this.analysis = analysis
        phase = Phase.RUNNING
        startedAt = SystemClock.elapsedRealtime()
        analysis.setAnalyzer(worker) { proxy -> analyze(proxy) }
    }

    private var startedAt = 0L

    private fun analyze(proxy: ImageProxy) {
        try {
            if (phase != Phase.RUNNING) return
            val frame = proxy.toUprightBitmap()
            try {
                if (mode == Mode.CARD) analyzeCard(frame) else analyzeSelfie(frame)
            } finally {
                if (!frame.isRecycled) {
                    // analyzeSelfie 가 보관한 프레임은 복사본이므로 원본은 항상 반환
                    frame.recycle()
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "analyze", t)
        } finally {
            proxy.close()
        }
    }

    // ───────────────────────── 학생증 앞면 얼굴 ─────────────────────────

    private fun analyzeCard(frame: Bitmap) {
        val faces = Tasks.await(cardDetector.process(InputImage.fromBitmap(frame, 0)))
        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }?.toInfo()
        if (face == null) { emit(activity.getString(R.string.face_card_none), false); return }
        if (face.box.width() < MIN_CARD_FACE_PX) { emit(activity.getString(R.string.face_card_small), false); return }

        val aligned = alignFace(frame, face)
        val sharp = sharpnessOf(frame, face.box)
        cardSamples.add(CardSample(aligned, sharp))
        emit(activity.getString(R.string.face_card_reading), true)
        if (cardSamples.size < CARD_SAMPLES) return

        phase = Phase.DONE
        // 화질이 낮으니 흐린 프레임은 버리고 선명한 몇 장만 골라 평균 (임베딩은 정렬+보정한 얼굴로)
        val best = cardSamples.sortedByDescending { it.sharp }.take(CARD_USE)
        if (embedder.available) {
            val embs = best.mapNotNull { embedder.embed(enhanceCardFace(it.aligned)) }
            FaceSession.cardEmbedding = if (embs.isNotEmpty()) FaceMath.meanEmbedding(embs) else null
        } else {
            FaceSession.cardEmbedding = null
        }
        cardSamples.forEach { it.aligned.recycle() }
        cardSamples.clear()
        activity.runOnUiThread { if (!released) listener.onCardFace() }
    }

    // ───────────────────────── 셀피 본인 확인 ─────────────────────────

    private fun analyzeSelfie(frame: Bitmap) {
        val faces = Tasks.await(selfieDetector.process(InputImage.fromBitmap(frame, 0)))
        if (faces.isEmpty()) { emit(activity.getString(R.string.face_self_hint), false); return }
        if (faces.size > 1) { emit(activity.getString(R.string.face_self_many), false); return }
        val face = faces[0].toInfo()
        val fw = frame.width.toFloat()

        // 얼굴이 틀 안 가운데에 적당한 크기로
        if (face.box.width() < 0.30f * fw) { emit(activity.getString(R.string.face_self_closer), false); return }
        val cx = face.box.exactCenterX()
        if (abs(cx - fw / 2f) > 0.18f * fw) { emit(activity.getString(R.string.face_self_center), false); return }
        val luma = meanLumaOf(frame, face.box)
        if (luma < LUMA_DARK) { emit(activity.getString(R.string.face_self_dark), false); return }
        if (luma > LUMA_GLARE) { emit(activity.getString(R.string.face_self_glare), false); return }
        if (abs(face.yaw) > MAX_POSE || abs(face.pitch) > MAX_POSE) { emit(activity.getString(R.string.face_self_front), false); return }

        val eyes = face.eyesOpen ?: 1f
        blink.feed(eyes)

        if (eyes >= 0.65f) {
            val sharp = sharpnessOf(frame, face.box)
            val frontal = 1.0 - (abs(face.yaw) + abs(face.pitch)) / 60.0
            keepGood(frame, face, sharp * frontal)
        }

        if (goods.size >= GOOD_NEEDED && blink.blinked) {
            phase = Phase.DONE
            emit(activity.getString(R.string.face_self_working), true)
            finishSelfie()
        } else if (goods.size >= GOOD_NEEDED) {
            emit(activity.getString(R.string.face_self_blink), true)
        } else {
            emit(activity.getString(R.string.face_self_hold), true)
        }
    }

    private fun keepGood(frame: Bitmap, face: FaceInfo, score: Double) {
        if (goods.size < GOOD_KEEP) {
            goods.add(GoodFrame(frame.copy(Bitmap.Config.ARGB_8888, false), face, score))
        } else {
            val worst = goods.minByOrNull { it.score } ?: return
            if (score > worst.score) {
                goods.remove(worst)
                worst.frame.recycle()
                goods.add(GoodFrame(frame.copy(Bitmap.Config.ARGB_8888, false), face, score))
            }
        }
    }

    private fun finishSelfie() {
        val best = goods.sortedByDescending { it.score }
        var matched: Boolean? = null
        var score: Float? = null
        val card = FaceSession.cardEmbedding
        if (embedder.available && card != null) {
            val embs = best.take(GOOD_KEEP).mapNotNull { g ->
                val a = alignFace(g.frame, g.face)
                val e = embedder.embed(a)
                a.recycle()
                e
            }
            if (embs.isNotEmpty()) {
                val s = FaceMath.cosine(FaceMath.meanEmbedding(embs), card)
                score = s
                matched = MatchPolicy.isMatch(s)
            } else {
                matched = false
            }
        }
        var png: ByteArray? = null
        if (matched != false) {
            val top = best.first()
            png = portrait.create(top.frame, top.face)
            FaceSession.pendingPortrait = png
        }
        goods.forEach { it.frame.recycle() }
        goods.clear()
        FaceSession.cardEmbedding = null          // 비교가 끝났으니 증명사진 특징값은 바로 지운다
        val result = SelfieResult(matched, score, png)
        activity.runOnUiThread { if (!released) listener.onSelfie(result) }
    }

    private fun emit(hint: String, detected: Boolean) {
        activity.runOnUiThread {
            if (released || phase == Phase.IDLE) return@runOnUiThread
            if (hint == lastHint && detected == lastDetected) return@runOnUiThread
            lastHint = hint
            lastDetected = detected
            listener.onState(hint, detected)
        }
    }

    private companion object {
        const val TAG = "FaceScanner"
        const val MIN_CARD_FACE_PX = 36        // 증명사진 속 얼굴이 이보다 작으면 더 가까이
        const val CARD_SAMPLES = 6             // 앞면에서 모을 프레임 수
        const val CARD_USE = 4                 // 그중 선명한 몇 장만 사용
        const val GOOD_NEEDED = 3              // 셀피: 정면·눈 뜬 선명한 프레임 최소 수
        const val GOOD_KEEP = 4
        const val MAX_POSE = 12f               // 정면 허용 각도(도)
        const val LUMA_DARK = 55f
        const val LUMA_GLARE = 225f
    }
}
