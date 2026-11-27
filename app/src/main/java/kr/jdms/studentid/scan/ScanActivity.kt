package kr.jdms.studentid.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kr.jdms.studentid.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 네이티브 학생증 스캔 화면.
 *
 *  1. CameraX 미리보기 + 분석(ImageAnalysis)으로 가이드 영역을 계속 분석
 *     - ML Kit 바코드(CODE_128/39/ITF) + ML Kit 한글 텍스트 인식(이름·학교명·학번)
 *  2. 여러 프레임의 결과가 같은 값으로 모이면(ScanConsensus) 고해상도 정지 촬영(ImageCapture)
 *  3. 정지 사진을 한 번 더 분석해 최종 확정 → 확인 시트(이름은 직접 수정 가능)
 *  4. 확인하면 이름/코드를 MainActivity로 돌려준다.
 */
class ScanActivity : AppCompatActivity() {

    private enum class Phase { STARTING, SCANNING, CAPTURING, VERIFIED, SHEET }

    private lateinit var previewView: PreviewView
    private lateinit var overlay: GuideOverlayView
    private lateinit var hintView: TextView
    private lateinit var torchButton: ImageButton
    private lateinit var flashView: View
    private lateinit var dimView: View
    private lateinit var sheet: View
    private lateinit var thumbView: ImageView
    private lateinit var nameEdit: EditText
    private lateinit var codeText: TextView
    private lateinit var nameNote: TextView

    private var phase = Phase.STARTING
    private val consensus = ScanConsensus()
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val barcodeScanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39, Barcode.FORMAT_ITF)
                .build(),
        )
    }
    private val textRecognizer by lazy { TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()) }

    private var camera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null
    private var torchOn = false

    @Volatile private var guideFrac = RectF(0.1f, 0.2f, 0.9f, 0.8f)

    private var scanStartedAt = 0L
    private var lastProgressAt = 0L
    private var codeReadySince = 0L
    private var lastStillFailAt = 0L
    private var hintLockedUntil = 0L
    private var pendingCode = ""

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else onPermissionDenied()
        }

    // ───────────────────────── 수명주기 ─────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        previewView = findViewById(R.id.preview)
        overlay = findViewById(R.id.overlay)
        hintView = findViewById(R.id.hint)
        torchButton = findViewById(R.id.btn_torch)
        flashView = findViewById(R.id.flash)
        dimView = findViewById(R.id.dim)
        sheet = findViewById(R.id.sheet)
        thumbView = findViewById(R.id.thumb)
        nameEdit = findViewById(R.id.name_edit)
        codeText = findViewById(R.id.code_text)
        nameNote = findViewById(R.id.name_note)

        val topBar = findViewById<View>(R.id.top_bar)
        val sheetPadBottom = sheet.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            topBar.setPadding(bars.left, bars.top, bars.right, 0)
            sheet.setPadding(sheet.paddingLeft, sheet.paddingTop, sheet.paddingRight, sheetPadBottom + maxOf(bars.bottom, ime.bottom))
            insets
        }

        thumbView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 14f * resources.displayMetrics.density)
            }
        }
        thumbView.clipToOutline = true

        findViewById<View>(R.id.btn_close).setOnClickListener { cancelAndFinish(null) }
        torchButton.setOnClickListener { toggleTorch() }
        findViewById<View>(R.id.btn_retry).setOnClickListener { retryScan() }
        findViewById<View>(R.id.btn_confirm).setOnClickListener { confirm() }
        nameEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { hideKeyboard(); true } else false
        }

        // 탭하면 그 지점으로 초점/노출 맞추기
        previewView.setOnTouchListener { v, e ->
            if (e.action == MotionEvent.ACTION_UP && phase == Phase.SCANNING) {
                val p = previewView.meteringPointFactory.createPoint(e.x, e.y)
                camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(p).build())
                v.performClick()
            }
            true
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = cancelAndFinish(null)
        })

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            cancelAndFinish(getString(R.string.fail_no_camera))
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        worker.shutdownNow()
        runCatching { barcodeScanner.close() }
        runCatching { textRecognizer.close() }
        super.onDestroy()
    }

    // ───────────────────────── 권한 / 종료 ─────────────────────────

    private fun onPermissionDenied() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            cancelAndFinish(getString(R.string.permission_denied))
        } else {
            // '다시 묻지 않음' 상태 → 설정 화면으로 안내
            AlertDialog.Builder(this)
                .setMessage(R.string.permission_settings_msg)
                .setPositiveButton(R.string.permission_open_settings) { _, _ ->
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
                    )
                    cancelAndFinish(null)
                }
                .setNegativeButton(R.string.cancel) { _, _ -> cancelAndFinish(getString(R.string.permission_denied)) }
                .setOnCancelListener { cancelAndFinish(getString(R.string.permission_denied)) }
                .show()
        }
    }

    private fun cancelAndFinish(error: String?) {
        setResult(RESULT_CANCELED, Intent().apply { error?.let { putExtra(EXTRA_ERROR, it) } })
        finish()
    }

    // ───────────────────────── 카메라 ─────────────────────────

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                previewView.doOnLayout { bindCamera(provider) }
            } catch (e: Exception) {
                Log.e(TAG, "camera provider", e)
                cancelAndFinish(getString(R.string.fail_camera))
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera(provider: ProcessCameraProvider) {
        val viewPort = previewView.viewPort
        if (viewPort == null) {
            cancelAndFinish(getString(R.string.fail_camera))
            return
        }
        updateGuideFraction()

        val ratio43 = AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO)
        fun selector(size: Size?, rule: Int) = ResolutionSelector.Builder()
            .setAspectRatioStrategy(ratio43)
            .apply { if (size != null) setResolutionStrategy(ResolutionStrategy(size, rule)) }
            .build()

        val preview = Preview.Builder()
            .setResolutionSelector(selector(null, 0))
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }

        // 분석: 작은 글씨(이름)까지 읽으려면 해상도가 어느 정도 필요
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(selector(Size(1920, 1440), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(worker, ImageAnalysis.Analyzer { proxy -> analyzeFrame(proxy) }) }

        // 정지 촬영: 약 800만 화소 (메모리와 정확도의 균형)
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(selector(Size(3264, 2448), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
            .build()

        val group = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(analysis)
            .addUseCase(capture)
            .setViewPort(viewPort)
            .build()

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, group)
        } catch (e: Exception) {
            Log.e(TAG, "bind", e)
            cancelAndFinish(getString(R.string.fail_camera))
            return
        }
        imageAnalysis = analysis
        imageCapture = capture
        torchButton.visibility = if (camera?.cameraInfo?.hasFlashUnit() == true) View.VISIBLE else View.GONE

        startScanning()
    }

    private fun updateGuideFraction() {
        val w = previewView.width.toFloat()
        val h = previewView.height.toFloat()
        if (w <= 0 || h <= 0) return
        val g = overlay.guideRect
        guideFrac = RectF(g.left / w, g.top / h, g.right / w, g.bottom / h)
    }

    private fun toggleTorch() {
        torchOn = !torchOn
        camera?.cameraControl?.enableTorch(torchOn)
        torchButton.setImageResource(if (torchOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off)
    }

    // ───────────────────────── 스캔 흐름 ─────────────────────────

    private fun startScanning() {
        consensus.reset()
        phase = Phase.SCANNING
        overlay.state = GuideOverlayView.State.IDLE
        overlay.hideCheck()
        val now = SystemClock.elapsedRealtime()
        scanStartedAt = now
        lastProgressAt = now
        codeReadySince = 0L
        lastStillFailAt = 0L
        hintLockedUntil = 0L
        setHint(getString(R.string.hint_default))
    }

    /** (백그라운드) 프레임 하나 분석 */
    private fun analyzeFrame(proxy: ImageProxy) {
        try {
            if (phase != Phase.SCANNING) return
            val upright = proxy.toUprightBitmap()
            val region = upright.cropFraction(guideFrac, MARGIN_FRAME)
            val luma = region.meanLuma()
            val obs = recognize(region)
            region.recycle()
            runOnUiThread { onFrameResult(obs, luma) }
        } catch (t: Throwable) {
            Log.w(TAG, "analyze", t)
        } finally {
            proxy.close()
        }
    }

    /** (백그라운드) 바코드 + 한글 OCR → 관측값 */
    private fun recognize(bitmap: Bitmap): Observation {
        val image = InputImage.fromBitmap(bitmap, 0)

        val barcode = try {
            Tasks.await(barcodeScanner.process(image))
                .asSequence()
                .mapNotNull { CardParser.normalizeCode(it.rawValue) }
                .firstOrNull()
        } catch (e: Exception) {
            null
        }

        val lines = try {
            Tasks.await(textRecognizer.process(image)).textBlocks
                .flatMap { it.lines }
                .map { l ->
                    val b = l.boundingBox
                    if (b != null) OcrLine(l.text, b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
                    else OcrLine(l.text)
                }
        } catch (e: Exception) {
            emptyList()
        }

        val parsed = CardParser.parse(lines)
        return Observation(
            barcode = barcode,
            ocrCode = parsed.ocrCode,
            name = parsed.name,
            hasSchool = parsed.hasSchool,
            hasTitle = parsed.hasTitle,
        )
    }

    private fun onFrameResult(obs: Observation, luma: Float) {
        if (phase != Phase.SCANNING) return
        val now = SystemClock.elapsedRealtime()
        consensus.add(obs)

        val cardLike = obs.hasSchool || obs.barcode != null
        overlay.state = if (cardLike) GuideOverlayView.State.DETECTED else GuideOverlayView.State.IDLE
        if (cardLike || obs.name != null) lastProgressAt = now

        val code = consensus.leadingCode()
        val name = consensus.leadingName()
        val school = consensus.schoolSeen
        if (school && code != null) {
            if (codeReadySince == 0L) codeReadySince = now
        } else {
            codeReadySince = 0L
        }
        // 이름만 끝내 못 읽는 경우: 일정 시간 뒤 이름은 직접 입력하도록 넘어간다
        val allowMissingName = codeReadySince != 0L && now - codeReadySince > NAME_FALLBACK_MS

        if (now >= hintLockedUntil) {
            setHint(
                when {
                    luma < LUMA_DARK -> getString(R.string.hint_dark)
                    luma > LUMA_GLARE -> getString(R.string.hint_glare)
                    school && code != null && name == null -> getString(R.string.hint_reading_name)
                    school && code != null -> getString(R.string.hint_card_seen)
                    school -> getString(R.string.hint_need_barcode)
                    code != null -> getString(R.string.hint_need_school)
                    now - lastProgressAt > LOST_MS -> getString(R.string.hint_lost)
                    else -> getString(R.string.hint_default)
                },
            )
        }

        if (consensus.isReady(allowMissingName) && now - lastStillFailAt > STILL_COOLDOWN_MS) {
            capture(allowMissingName)
        }
    }

    private fun capture(allowMissingName: Boolean) {
        val ic = imageCapture ?: return
        phase = Phase.CAPTURING
        setHint(getString(R.string.hint_capturing))
        flashView.animate().cancel()
        flashView.alpha = 0.9f
        flashView.animate().alpha(0f).setDuration(300).start()

        ic.takePicture(worker, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                var region: Bitmap? = null
                var obs: Observation? = null
                try {
                    val upright = image.toUprightBitmap()
                    region = upright.cropFraction(guideFrac, MARGIN_STILL, maxSide = STILL_MAX_SIDE)
                    obs = recognize(region)
                } catch (t: Throwable) {
                    Log.w(TAG, "still", t)
                } finally {
                    image.close()
                }
                val r = region
                val o = obs
                runOnUiThread { onStillResult(r, o, allowMissingName) }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.w(TAG, "capture error", exception)
                runOnUiThread { backToScanning(getString(R.string.fail_capture), 1200) }
            }
        })
    }

    private fun onStillResult(region: Bitmap?, obs: Observation?, allowMissingName: Boolean) {
        if (isFinishing || isDestroyed || phase != Phase.CAPTURING) { region?.recycle(); return }
        if (region == null || obs == null) {
            region?.recycle()
            backToScanning(getString(R.string.fail_capture), 1200)
            return
        }
        consensus.add(obs, ScanConsensus.STILL_WEIGHT)
        val result = consensus.result(allowMissingName)
        if (result != null) {
            onVerified(result, region)
        } else {
            val reason = when {
                !consensus.schoolSeen -> R.string.fail_not_card
                consensus.leadingCode() == null -> R.string.fail_no_barcode
                else -> R.string.fail_no_name
            }
            region.recycle()
            backToScanning(getString(reason), 1500)
        }
    }

    private fun backToScanning(message: String, lockMs: Long) {
        if (isFinishing || isDestroyed) return
        phase = Phase.SCANNING
        lastStillFailAt = SystemClock.elapsedRealtime()
        overlay.state = GuideOverlayView.State.IDLE
        hintLockedUntil = lastStillFailAt + lockMs
        setHint(message)
    }

    private fun onVerified(result: ScanResult, thumb: Bitmap) {
        phase = Phase.VERIFIED
        pendingCode = result.code
        overlay.state = GuideOverlayView.State.VERIFIED
        overlay.showCheck()
        setHint(getString(R.string.hint_verified))
        hintLockedUntil = Long.MAX_VALUE
        previewView.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
        )

        thumbView.setImageBitmap(thumb)
        codeText.text = result.code
        nameEdit.setText(result.name.orEmpty())
        if (result.name == null) {
            nameNote.setText(R.string.sheet_name_manual)
            nameNote.setTextColor(Color.parseColor("#D93025"))
        } else {
            nameNote.setText(R.string.sheet_name_edit_hint)
            nameNote.setTextColor(ContextCompat.getColor(this, R.color.sheet_text_sub))
        }

        // 확인 시트가 올라오기 전에 분석은 멈춘다
        imageAnalysis?.clearAnalyzer()
        previewView.postDelayed({ if (phase == Phase.VERIFIED) openSheet() }, 900)
    }

    private fun openSheet() {
        phase = Phase.SHEET
        dimView.visibility = View.VISIBLE
        dimView.animate().alpha(1f).setDuration(300).start()
        sheet.visibility = View.INVISIBLE
        sheet.doOnLayout {
            sheet.translationY = sheet.height.toFloat()
            sheet.visibility = View.VISIBLE
            sheet.animate().translationY(0f).setDuration(450).start()
        }
    }

    private fun closeSheet(onEnd: () -> Unit) {
        hideKeyboard()
        dimView.animate().alpha(0f).setDuration(250).withEndAction { dimView.visibility = View.GONE }.start()
        sheet.animate().translationY(sheet.height.toFloat()).setDuration(300).withEndAction {
            sheet.visibility = View.INVISIBLE
            onEnd()
        }.start()
    }

    private fun retryScan() {
        if (phase != Phase.SHEET) return
        closeSheet {
            thumbView.setImageDrawable(null)
            imageAnalysis?.setAnalyzer(worker, ImageAnalysis.Analyzer { proxy -> analyzeFrame(proxy) })
            startScanning()
        }
    }

    private fun confirm() {
        if (phase != Phase.SHEET) return
        val name = nameEdit.text.toString().trim().replace(Regex("\\s+"), " ")
        if (name.length < 2 || name.any { !it.isLetter() && it != ' ' }) {
            nameEdit.error = getString(R.string.sheet_name_invalid)
            nameEdit.requestFocus()
            return
        }
        hideKeyboard()
        setResult(
            RESULT_OK,
            Intent().putExtra(EXTRA_NAME, name).putExtra(EXTRA_CODE, pendingCode),
        )
        finish()
    }

    // ───────────────────────── 보조 ─────────────────────────

    private fun setHint(text: String) {
        if (hintView.text.toString() != text) hintView.text = text
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(nameEdit.windowToken, 0)
        nameEdit.clearFocus()
    }

    companion object {
        private const val TAG = "ScanActivity"
        const val EXTRA_NAME = "name"
        const val EXTRA_CODE = "code"
        const val EXTRA_ERROR = "error"

        private const val MARGIN_FRAME = 0.04f   // 가이드 틀 바깥으로 살짝 여유 (카드가 약간 어긋나도 읽히도록)
        private const val MARGIN_STILL = 0.06f
        private const val STILL_MAX_SIDE = 2400
        private const val LUMA_DARK = 35f
        private const val LUMA_GLARE = 235f
        private const val NAME_FALLBACK_MS = 7_000L
        private const val STILL_COOLDOWN_MS = 1_500L
        private const val LOST_MS = 10_000L
    }
}
