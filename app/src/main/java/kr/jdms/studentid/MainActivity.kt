package kr.jdms.studentid

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
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
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewFeature
import kr.jdms.studentid.scan.CardParser
import kr.jdms.studentid.scan.ScanActivity
import org.json.JSONObject

/**
 * 학생증 HTML(assets/index.html)을 WebView로 보여주는 메인 화면.
 *
 * - 세이프 영역: WebView는 화면 전체(상태바·내비게이션바 뒤까지)를 쓰고, 시스템 inset(상태바/노치/제스처바 높이)을
 *   HTML 템플릿의 safe-area CSS 변수로 자동 주입한다. → 배경 그라데이션은 바 뒤까지 이어지고 콘텐츠만 안전 영역 안에 배치된다.
 * - 학생증 스캔(촬영·바코드·OCR)은 ScanActivity(네이티브)가 처리하고, 결과만 JS로 넘긴다.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var store: CardStore
    private var scanLaunched = false
    private var webViewDestroyed = false
    private var pageLoaded = false

    /** 웹(CSS px = dp) 기준 세이프 영역 */
    private data class SafeInsets(val top: Float, val right: Float, val bottom: Float, val left: Float)

    @Volatile private var safeInsets = SafeInsets(0f, 0f, 0f, 0f)

    private val scanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            scanLaunched = false
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                val name = data.getStringExtra(ScanActivity.EXTRA_NAME).orEmpty()
                val code = data.getStringExtra(ScanActivity.EXTRA_CODE).orEmpty()
                if (name.isNotBlank() && CardParser.isValidCode(code) && store.save(name, code)) {
                    js("window.__sidRegister && window.__sidRegister(${JSONObject.quote(name)}, ${JSONObject.quote(code)})")
                }
            } else {
                data?.getStringExtra(ScanActivity.EXTRA_ERROR)?.let {
                    js("window.__sidToast && window.__sidToast(${JSONObject.quote(it)})")
                }
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        store = CardStore(this)

        root = FrameLayout(this).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.app_bg))
        }
        setContentView(root)

        createWebView()

        // ── 세이프 영역: 상태바/노치/내비게이션바 높이를 읽어 웹에 전달 ──
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            val d = resources.displayMetrics.density    // 웹의 CSS px = dp
            safeInsets = SafeInsets(bars.top / d, bars.right / d, bars.bottom / d, bars.left / d)
            if (!pageLoaded) {
                loadPage()          // 첫 inset을 알게 된 뒤에 로드 → 첫 화면부터 올바른 여백
            } else {
                val s = safeInsets
                js("window.__sidSetInsets && window.__sidSetInsets(${s.top}, ${s.right}, ${s.bottom}, ${s.left})")
            }
            WindowInsetsCompat.CONSUMED
        }
        root.postDelayed({ loadPage() }, 800)   // inset 콜백이 안 오는 드문 경우 대비

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = onBack()
        })
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun createWebView() {
        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain(ASSET_HOST)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(ContextCompat.getColor(context, R.color.app_bg))
            overScrollMode = WebView.OVER_SCROLL_NEVER

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mediaPlaybackRequiresUserGesture = false
                setSupportZoom(false)
                builtInZoomControls = false
                textZoom = 100                       // 시스템 글꼴 크기와 상관없이 디자인대로
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            // 페이지가 자체 다크모드(prefers-color-scheme)를 갖고 있으므로 강제 반전은 끈다
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
            }

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    assetLoader.shouldInterceptRequest(request.url)

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url
                    if (url.host == ASSET_HOST) return false
                    // 앱 밖 주소는 WebView 안에서 열지 않고 브라우저로 보낸다
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

            addJavascriptInterface(Bridge(), "SidNative")
        }
        root.addView(webView)
    }

    private fun loadPage() {
        if (pageLoaded || isDestroyed || webViewDestroyed || !::webView.isInitialized) return
        pageLoaded = true
        webView.loadUrl("https://$ASSET_HOST/assets/index.html")
    }

    // ───────────────────────── 뒤로가기 ─────────────────────────

    /**
     * 홈 화면이 아니거나(다른 탭) 설정·정보 같은 페이지가 열려 있으면 웹 쪽에서 한 단계 뒤로,
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

    // ───────────────────────── JS 연동 ─────────────────────────

    private fun js(code: String) {
        if (::webView.isInitialized) webView.evaluateJavascript(code, null)
    }

    private fun launchScan() {
        if (scanLaunched) return
        scanLaunched = true
        scanLauncher.launch(Intent(this, ScanActivity::class.java))
    }

    /** 웹 페이지(window.SidNative)에서 부르는 네이티브 기능. 우리 앱의 asset 주소에서만 로드되므로 외부 페이지는 접근 불가. */
    inner class Bridge {
        @JavascriptInterface
        fun startScan() {
            runOnUiThread { launchScan() }
        }

        /** 세이프 영역(dp): {"top":..,"right":..,"bottom":..,"left":..} */
        @JavascriptInterface
        fun getInsets(): String {
            val s = safeInsets
            return JSONObject().put("top", s.top.toDouble()).put("right", s.right.toDouble())
                .put("bottom", s.bottom.toDouble()).put("left", s.left.toDouble()).toString()
        }

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
        root.removeAllViews()
        if (!webViewDestroyed) webView.destroy()
        super.onDestroy()
    }

    private companion object {
        const val ASSET_HOST = "appassets.androidplatform.net"

        // 웹 앱의 뒤로가기 규칙(HTML 내부 'back' 핸들러)과 같은 기준: 홈 탭이 아니거나 열린 페이지가 2개 이상이면 뒤로 갈 곳이 있음
        const val PROBE_CAN_POP_JS =
            "(function(){try{var t=document.querySelector('wx-tab-control');var tab=t?t.getAttribute('selected'):null;" +
                "var pages=document.querySelectorAll('px-window-glass.plugin-page').length;" +
                "return !!((tab&&tab!=='home')||pages>1);}catch(e){return false;}})()"

        const val DISPATCH_BACK_JS =
            "window.dispatchEvent(new CustomEvent('back',{cancelable:true,bubbles:false}))"
    }
}
