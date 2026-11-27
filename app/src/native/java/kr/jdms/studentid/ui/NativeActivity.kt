package kr.jdms.studentid.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kr.jdms.studentid.CardStore
import kr.jdms.studentid.R
import kr.jdms.studentid.face.SFaceEmbedder

/**
 * 네이티브(Compose) 버전의 메인 화면. 웹 버전과 같은 기능/문구/흐름이며 화면은 Compose 로 직접 그린다.
 * (색·크기·반지름은 HTML 템플릿을 렌더링해서 측정한 값)
 */
class NativeActivity : AppCompatActivity() {
    private lateinit var state: AppState
    private lateinit var scan: ScanController
    private val toast = ToastState()
    private val embedder = lazy(LazyThreadSafetyMode.SYNCHRONIZED) { SFaceEmbedder(applicationContext) }

    private var cameraCb: ((Boolean) -> Unit)? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        cameraCb?.invoke(ok)
        cameraCb = null
    }

    /** 카메라 권한이 있으면 바로, 없으면 요청한 뒤 결과를 넘겨준다 */
    fun ensureCamera(cb: (Boolean) -> Unit) {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) { toast.show("이 기기에서는 카메라를 사용할 수 없어요"); cb(false); return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) cb(true)
        else { cameraCb = cb; permissionLauncher.launch(Manifest.permission.CAMERA) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        state = AppState(this, CardStore(this))
        scan = ScanController(this, state, toast, embedder)
        scan.close = { state.page = Page.NONE }
        scan.prewarm()
        Thread { embedder.value }.start()          // 얼굴 비교 모델은 용량이 커서 미리 올려 둔다

        setContent {
            val dark = isSystemInDarkTheme()
            // 테마가 바뀌어도 액티비티를 다시 만들지 않으므로(깜빡임 방지) 시스템 바 아이콘 색은 여기서 맞춘다
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose { }
            }
            AppTheme(dark) { Root() }
        }
    }

    override fun onDestroy() {
        scan.release()
        if (embedder.isInitialized()) embedder.value.close()
        super.onDestroy()
    }

    @Composable
    private fun Root() {
        val c = LocalColors.current
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        var showPhoto by remember { mutableStateOf(false) }

        // 학생증 탭에서는 화면 밝기를 최대로 (설정 '학생증 화면 밝기 높이기')
        LaunchedEffect(state.tab, state.page, state.brightBoost) {
            val boost = state.brightBoost && state.tab == Tab.CARD && state.page == Page.NONE
            window.attributes = window.attributes.also { it.screenBrightness = if (boost) 1f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }

        // 뒤로가기: 페이지 → 닫기, 다른 탭 → 홈, 홈 → 종료(기본 동작)
        BackHandler(enabled = state.page != Page.NONE || state.tab != Tab.HOME) {
            when (state.page) {
                Page.NONE -> state.tab = Tab.HOME
                Page.LICENSES -> state.page = Page.INFO
                else -> state.page = Page.NONE
            }
        }

        Box(Modifier.fillMaxSize().background(c.pageBg)) {
            // 라이트 홈/학생증 탭은 템플릿의 그라데이션 배경 (다크는 평면)
            if (!c.dark && state.page == Page.NONE && state.tab != Tab.MENU) {
                Image(painterResource(if (state.tab == Tab.HOME) R.drawable.bg_home else R.drawable.bg_devices), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }

            Box(Modifier.fillMaxSize().padding(top = top)) {
                when (state.page) {
                    Page.NONE -> when (state.tab) {
                        Tab.HOME -> HomeTab(state, onScan = ::openScan, onOpenCard = { state.tab = Tab.CARD }, onSoon = { toast.show("데모에서는 준비 중이에요") })
                        Tab.CARD -> IdTab(state, onScan = ::openScan, onPhoto = { showPhoto = true })
                        Tab.MENU -> MenuTab(onReRegister = ::openScan, onTips = { state.page = Page.TIPS }, onInfo = { state.page = Page.INFO }, onSettings = { state.page = Page.SETTINGS })
                    }
                    Page.SETTINGS -> SettingsPage(state, { state.page = Page.NONE }, onReRegister = ::openScan, onUnregister = {
                        if (state.registered) { state.unregister(); toast.show("등록된 학생증을 삭제했어요") } else toast.show("등록된 학생증이 없어요")
                    })
                    Page.TIPS -> TipsPage { state.page = Page.NONE }
                    Page.INFO -> InfoPage({ state.page = Page.NONE }, { state.page = Page.LICENSES })
                    Page.LICENSES -> LicensesPage { state.page = Page.INFO }
                    Page.SCAN -> ScanScreen(scan) { state.page = Page.NONE }
                }
            }

            if (state.page == Page.NONE) {
                TabBar(state.tab, { state.tab = it }, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp + bottom))
            }

            val zoom = if (showPhoto && state.photo != null) {
                DialogSpec(
                    title = state.name ?: "프로필 사진",
                    content = {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Image(state.photo!!, "프로필 사진", Modifier.size(280.dp, 373.dp).clip(RoundedCornerShape(24.dp)).background(c.tile), contentScale = ContentScale.Crop)
                        }
                    },
                    actions = listOf(DialogAction("닫기") { showPhoto = false }),
                    onCancel = { showPhoto = false },
                )
            } else null
            DialogOverlay(scan.dialog ?: zoom, bottom)
            ToastHost(toast, bottom)
        }
    }

    private fun openScan() {
        scan.begin()
        state.page = Page.SCAN
    }
}
