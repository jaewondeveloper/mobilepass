package kr.jdms.studentid

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Outline
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.accessibility.AccessibilityManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewFeature
import kr.jdms.studentid.face.FaceScanner
import kr.jdms.studentid.face.FaceSession
import kr.jdms.studentid.face.SFaceEmbedder
import kr.jdms.studentid.scan.CardScanner
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 학생증 HTML(assets/index.html)을 WebView로 보여주는 메인 화면.
 *
 * - **vhome 브리지**: HTML 템플릿이 앱에서 기대하는 네이티브 규약(`window.vhome`)을 구현한다.
 *   상태바/내비게이션바 높이는 실제 시스템 inset 으로 매번 계산해 `getSystemBarInsets()` 로 넘기고,
 *   템플릿이 스스로 안전 영역·팝업·드롭다운 위치를 계산한다. (고정 여백 값 없음)
 * - **스캔**: 화면(헤더·안내·확인 모달)은 템플릿 부품이 그리고, 카드 모양 영역에만 네이티브 카메라 미리보기를
 *   겹쳐 보여준다. 촬영/바코드/OCR 은 [CardScanner] 가 처리한다.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var previewView: PreviewView
    private lateinit var store: CardStore
    private var scanner: CardScanner? = null
    private var faceScanner: FaceScanner? = null

    /** 얼굴 비교 모델은 용량이 커서 앱 시작 때 백그라운드에서 미리 올린다 */
    private val embedder = lazy(LazyThreadSafetyMode.SYNCHRONIZED) { SFaceEmbedder(applicationContext) }
    private var camMode = MODE_ID

    private var webViewDestroyed = false
    private var pageLoaded = false

    /** 웹(CSS px = dp) 기준 시스템 바 높이 */
    private data class BarInsets(val top: Double, val left: Double, val right: Double, val bottom: Double)

    @Volatile private var barInsets = BarInsets(0.0, 0.0, 0.0, 0.0)

    // 카메라가 비출 카드 영역 (CSS px)
    private var camX = 0.0
    private var camY = 0.0
    private var camW = 0.0
    private var camH = 0.0
    private var camRadius = 0.0
    private var cameraWanted = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && cameraWanted) startCamera() else if (!granted) scanError(getString(R.string.err_permission))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        store = CardStore(this)

        root = FrameLayout(this).apply { setBackgroundColor(ContextCompat.getColor(context, R.color.app_bg)) }
        setContentView(root)

        createWebView()
        createPreview()

        // 시스템 바 높이를 읽어 웹(vhome)에 전달. 첫 값을 알게 된 뒤에 페이지를 로드해서 첫 화면부터 정확한 여백.
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val d = resources.displayMetrics.density.toDouble()
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val status = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val cut = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            fun dp(px: Int) = (px / d * 10).roundToInt() / 10.0
            val next = BarInsets(
                top = dp(max(status.top, cut.top)),
                left = dp(max(sys.left, cut.left)),
                right = dp(max(sys.right, cut.right)),
                bottom = dp(max(sys.bottom, cut.bottom)),
            )
            val changed = next != barInsets
            barInsets = next
            if (!pageLoaded) loadPage()
            else if (changed) js("window.dispatchEvent(new Event('resize'))")   // 템플릿이 insets 를 다시 읽도록
            WindowInsetsCompat.CONSUMED
        }
        root.postDelayed({ loadPage() }, 800)   // inset 콜백이 안 오는 드문 경우 대비

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = onBack()
        })

        scanner = CardScanner(this, previewView, scanListener).also { it.prewarm() }
        Thread { embedder.value }.start()
    }

    // ───────────────────────── WebView ─────────────────────────

    private fun isDark() =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /**
     * 테마(라이트/다크) 전환으로 액티비티가 다시 만들어지면 페이지가 통째로 다시 로드되며 깜빡인다.
     * 매니페스트에서 uiMode 변경을 직접 처리하도록 해서(재생성 없음) 페이지에 값만 바로 알려준다.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val bg = ContextCompat.getColor(this, R.color.app_bg)
        root.setBackgroundColor(bg)
        if (::webView.isInitialized) webView.setBackgroundColor(bg)
        js("window.__sidSetDark && window.__sidSetDark(${isDark()})")
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun createWebView() {
        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain(ASSET_HOST)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) WebView.setWebContentsDebuggingEnabled(true)

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(ContextCompat.getColor(context, R.color.app_bg))
            overScrollMode = WebView.OVER_SCROLL_NEVER

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                setSupportZoom(false)
                builtInZoomControls = false
                textZoom = 100                       // 시스템 글꼴 크기와 상관없이 디자인대로
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            applyDarkModeSettings(settings)

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    assetLoader.shouldInterceptRequest(request.url)

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url
                    if (url.host == ASSET_HOST) return false
                    if (url.scheme == "http" || url.scheme == "https") {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, url)) }
                    }
                    return true
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    // 메모리 부족 등으로 렌더러가 죽으면 앱을 새로 띄운다 (학생증 정보는 저장돼 있음)
                    Toast.makeText(this@MainActivity, R.string.webview_crashed, Toast.LENGTH_SHORT).show()
                    root.removeView(view)
                    view.destroy()
                    webViewDestroyed = true
                    recreate()
                    return true
                }
            }

            // 템플릿이 기대하는 네이티브 규약 + 학생증 전용 기능
            addJavascriptInterface(VHome(), "vhome")
            addJavascriptInterface(Bridge(), "SidNative")
        }
        root.addView(webView)
    }

    /**
     * 다크모드: 페이지가 자체 다크 테마(prefers-color-scheme)를 갖고 있으므로
     * WebView 가 앱 테마(라이트/다크)를 그대로 미디어 쿼리에 반영하도록 켜 둔다.
     * (페이지가 다크를 지원하면 WebView 는 색을 임의로 뒤집지 않는다)
     */
    @Suppress("DEPRECATION")
    private fun applyDarkModeSettings(settings: WebSettings) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_AUTO)
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                WebSettingsCompat.setForceDarkStrategy(settings, WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING)
            }
        }
    }

    private fun loadPage() {
        if (pageLoaded || isDestroyed || webViewDestroyed || !::webView.isInitialized) return
        pageLoaded = true
        webView.loadUrl("https://$ASSET_HOST/assets/index.html")
    }

    private fun js(code: String) {
        if (::webView.isInitialized && !webViewDestroyed) webView.evaluateJavascript(code, null)
    }

    // ───────────────────────── 카메라 미리보기 (카드 모양 영역) ─────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun createPreview() {
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE   // 모서리 둥글게 자르기 위해 TextureView
            scaleType = PreviewView.ScaleType.FILL_CENTER
            visibility = View.GONE
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    if (camRadius < 0) outline.setOval(0, 0, view.width, view.height)       // 얼굴 확인: 타원
                    else outline.setRoundRect(0, 0, view.width, view.height, (camRadius * resources.displayMetrics.density).toFloat())
                }
            }
            clipToOutline = true
            setOnTouchListener { v, e ->
                if (e.action == MotionEvent.ACTION_UP) {
                    scanner?.focusAt(e.x, e.y)
                    v.performClick()
                }
                true
            }
        }
        root.addView(previewView, FrameLayout.LayoutParams(1, 1))
    }

    /** CSS px 좌표(웹뷰는 화면 전체이므로 그대로 화면 좌표) → 미리보기 위치/크기. 크기가 바뀌면 true */
    private fun applyCameraRect(): Boolean {
        val d = resources.displayMetrics.density
        val w = (camW * d).roundToInt().coerceAtLeast(1)
        val h = (camH * d).roundToInt().coerceAtLeast(1)
        val lp = previewView.layoutParams as FrameLayout.LayoutParams
        val sizeChanged = lp.width != w || lp.height != h
        lp.width = w
        lp.height = h
        lp.leftMargin = (camX * d).roundToInt()
        lp.topMargin = (camY * d).roundToInt()
        previewView.layoutParams = lp
        previewView.invalidateOutline()
        return sizeChanged
    }

    private fun startCamera() {
        if (!cameraWanted) return
        applyCameraRect()
        previewView.visibility = View.VISIBLE
        previewView.doOnLayout {
            when (camMode) {
                MODE_ID -> { faceScanner?.stop(); scanner?.start() }
                MODE_CARD_FACE -> { scanner?.stop(); faceScanner().start(FaceScanner.Mode.CARD) }
                MODE_SELFIE -> { scanner?.stop(); faceScanner().start(FaceScanner.Mode.SELFIE) }
            }
        }
    }

    private fun faceScanner(): FaceScanner =
        faceScanner ?: FaceScanner(this, previewView, embedder.value, faceListener).also { faceScanner = it }

    private fun stopCamera() {
        cameraWanted = false
        scanner?.stop()
        faceScanner?.stop()
        previewView.visibility = View.GONE
    }

    private fun scanError(message: String) {
        stopCamera()
        js("window.__sidScanError && window.__sidScanError(${JSONObject.quote(message)})")
    }

    private val scanListener = object : CardScanner.Listener {
        override fun onState(hint: String, detected: Boolean) {
            js("window.__sidScanState && window.__sidScanState(${JSONObject.quote(hint)}, $detected)")
        }

        override fun onResult(name: String, code: String, thumbJpeg: ByteArray) {
            // 확인 모달이 카메라 위에 보이도록 미리보기는 숨기고 카메라는 끈다 (다시 스캔하면 다시 켠다)
            scanner?.stop()
            previewView.visibility = View.INVISIBLE
            val thumb = "data:image/jpeg;base64," + Base64.encodeToString(thumbJpeg, Base64.NO_WRAP)
            js("window.__sidScanResult && window.__sidScanResult(${JSONObject.quote(name)}, ${JSONObject.quote(code)}, ${JSONObject.quote(thumb)})")
        }

        override fun onError(message: String) = scanError(message)
    }

    private val faceListener = object : FaceScanner.Listener {
        override fun onState(hint: String, detected: Boolean) {
            js("window.__sidScanState && window.__sidScanState(${JSONObject.quote(hint)}, $detected)")
        }

        override fun onCardFace() {
            faceScanner?.stop()
            previewView.visibility = View.INVISIBLE
            js("window.__sidFaceCard && window.__sidFaceCard()")
        }

        override fun onSelfie(result: FaceScanner.SelfieResult) {
            faceScanner?.stop()
            previewView.visibility = View.INVISIBLE
            val png = result.png
            val o = JSONObject()
                .put("matched", result.matched ?: JSONObject.NULL)
                .put("score", result.score?.toDouble() ?: JSONObject.NULL)
                .put("photo", if (png != null) "data:image/png;base64," + Base64.encodeToString(png, Base64.NO_WRAP) else JSONObject.NULL)
            js("window.__sidFaceSelfie && window.__sidFaceSelfie($o)")
        }

        override fun onError(message: String) = scanError(message)
    }

    // ───────────────────────── 뒤로가기 ─────────────────────────

    /**
     * 홈 화면이 아니거나(다른 탭) 설정·스캔 같은 페이지가 열려 있으면 웹 쪽에서 한 단계 뒤로,
     * 홈 화면이면 앱을 종료한다.
     */
    private fun onBack() {
        webView.evaluateJavascript(PROBE_CAN_POP_JS) { r ->
            if (r == "true") {
                if (webView.canGoBack()) webView.goBack() else webView.evaluateJavascript(DISPATCH_BACK_JS, null)
            } else {
                finish()
            }
        }
    }

    // ───────────────────────── JS 브리지 ─────────────────────────

    /**
     * 템플릿(SmartThings 플러그인 페이지)이 앱에서 기대하는 네이티브 규약.
     * HTML 안의 같은 이름 샘플(브라우저용)은 이 객체가 있으면 쓰이지 않는다.
     */
    inner class VHome {
        @JavascriptInterface
        fun getSystemBarInsets(): String {
            val b = barInsets
            return JSONObject()
                .put("unit", "dp")
                .put("statusBar", JSONObject().put("top", b.top).put("right", 0).put("bottom", 0).put("left", 0))
                .put("navigationBar", JSONObject().put("top", 0).put("right", b.right).put("bottom", b.bottom).put("left", b.left))
                .toString()
        }

        /** 템플릿이 페이지 색 모드에 맞춰 호출: 1 = 라이트(어두운 아이콘), 0 = 다크(밝은 아이콘) */
        @JavascriptInterface
        fun setSystemBarColor(mode: Int) {
            runOnUiThread {
                val c = WindowCompat.getInsetsController(window, window.decorView)
                c.isAppearanceLightStatusBars = mode == 1
                c.isAppearanceLightNavigationBars = mode == 1
            }
        }

        @JavascriptInterface
        fun close() {
            runOnUiThread { finish() }
        }

        @JavascriptInterface
        fun getDeviceMemory(): Int {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            return max(1, (info.totalMem / (1024.0 * 1024 * 1024)).roundToInt())
        }

        @JavascriptInterface
        fun isSupport64Bit(): Boolean = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()

        @JavascriptInterface
        fun isTalkbackEnabled(): Boolean =
            (getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager).isTouchExplorationEnabled

        @JavascriptInterface
        fun isHighContrastFontsEnabled(): Boolean =
            Settings.Secure.getInt(contentResolver, "high_text_contrast_enabled", 0) == 1

        // 학생증 앱에서는 쓰지 않는 기능 (호출되어도 무시)
        @JavascriptInterface fun launchBuyDevice(a: String?, b: String?) {}
        @JavascriptInterface fun launchHowToUse() {}
        @JavascriptInterface fun launchNotices() {}

        @JavascriptInterface fun logDebug(message: String?, tag: String?) { Log.d(tag ?: "vhome", message ?: "") }
        @JavascriptInterface fun logInfo(message: String?, tag: String?) { Log.i(tag ?: "vhome", message ?: "") }
        @JavascriptInterface fun logError(message: String?, tag: String?) { Log.e(tag ?: "vhome", message ?: "") }
    }

    /** 학생증 전용: 등록 정보 저장/복원, 스캔 카메라 제어 */
    inner class Bridge {
        /** 현재 앱의 다크모드 여부. 페이지의 색 모드를 이 값으로 고정한다. */
        @JavascriptInterface
        fun isDark(): Boolean = isDark()

        @JavascriptInterface
        fun getRegistration(): String? = store.toJson()

        @JavascriptInterface
        fun saveRegistration(name: String?, code: String?) {
            if (name != null && code != null) store.save(name, code)
        }

        @JavascriptInterface
        fun clearRegistration() {
            store.clear()
        }

        /**
         * 스캔 페이지의 틀(CSS px) 위에 카메라를 비추기 시작.
         * mode: "id"(학생증 글자/바코드) · "cardface"(앞면 얼굴, 후면) · "selfie"(본인 확인, 전면). radius < 0 이면 타원.
         */
        @JavascriptInterface
        fun cameraStart(mode: String, x: Double, y: Double, w: Double, h: Double, radius: Double) {
            runOnUiThread {
                camMode = mode
                camX = x; camY = y; camW = w; camH = h; camRadius = radius
                cameraWanted = true
                if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                    scanError(getString(R.string.err_no_camera))
                } else if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    startCamera()
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        }

        /** 페이지가 움직이거나(전환 애니메이션) 크기가 바뀔 때 위치 갱신 */
        @JavascriptInterface
        fun cameraRect(x: Double, y: Double, w: Double, h: Double) {
            runOnUiThread {
                camX = x; camY = y; camW = w; camH = h
                if (previewView.visibility == View.VISIBLE && applyCameraRect() && camMode == MODE_ID) scanner?.rebind()
            }
        }

        @JavascriptInterface
        fun cameraStop() {
            runOnUiThread { stopCamera() }
        }

        /** 얼굴 비교 모델이 앱에 들어 있는지 (없으면 본인 '비교'는 건너뛰고 프로필 사진만 만든다) */
        @JavascriptInterface
        fun faceMatchAvailable(): Boolean = embedder.value.available

        /** 확인된 프로필 사진을 저장하고 data URL 을 돌려준다. 임시로 들고 있던 얼굴 정보는 지운다. */
        @JavascriptInterface
        fun commitProfile(): String? {
            val png = FaceSession.pendingPortrait
            FaceSession.clear()
            if (png != null) store.saveProfile(png)
            return store.profileDataUrl()
        }

        /** 얼굴 단계를 취소/실패로 끝낼 때: 임시로 들고 있던 증명사진 특징값·프로필 후보를 지운다 */
        @JavascriptInterface
        fun discardFace() {
            FaceSession.clear()
        }

        @JavascriptInterface
        fun getProfile(): String? = store.profileDataUrl()

        /** 확인 모달에서 '다시 스캔' */
        @JavascriptInterface
        fun cameraResume() {
            runOnUiThread {
                cameraWanted = true
                startCamera()
            }
        }
    }

    // ───────────────────────── 수명주기 ─────────────────────────

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        scanner?.release()
        faceScanner?.release()
        FaceSession.clear()
        if (embedder.isInitialized()) embedder.value.close()
        root.removeAllViews()
        if (!webViewDestroyed) webView.destroy()
        super.onDestroy()
    }

    private companion object {
        const val ASSET_HOST = "appassets.androidplatform.net"
        const val MODE_ID = "id"
        const val MODE_CARD_FACE = "cardface"
        const val MODE_SELFIE = "selfie"

        // 웹 앱의 뒤로가기 규칙(HTML 내부 'back' 핸들러)과 같은 기준: 홈 탭이 아니거나 열린 페이지가 2개 이상이면 뒤로 갈 곳이 있음
        const val PROBE_CAN_POP_JS =
            "(function(){try{var t=document.querySelector('wx-tab-control');var tab=t?t.getAttribute('selected'):null;" +
                "var pages=document.querySelectorAll('px-window-glass.plugin-page').length;" +
                "return !!((tab&&tab!=='home')||pages>1);}catch(e){return false;}})()"

        const val DISPATCH_BACK_JS =
            "window.dispatchEvent(new CustomEvent('back',{cancelable:true,bubbles:false}))"
    }
}
