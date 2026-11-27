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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
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
        scan.close = { state.closeAll() }
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
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        // 학생증 탭에서는 화면 밝기를 최대로 (설정 '학생증 화면 밝기 높이기')
        LaunchedEffect(state.tab, state.page, state.brightBoost) {
            val boost = state.brightBoost && state.tab == Tab.CARD && state.page == Page.NONE
            window.attributes = window.attributes.also { it.screenBrightness = if (boost) 1f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }

        AppSurface(
            state = state, toast = toast, scanDialog = scan.dialog, top = top, bottom = bottom,
            openScan = ::openScan,
            scanScreen = { ScanScreen(scan) { state.closeAll() } },
        )
    }

    private fun openScan() {
        scan.begin()
        state.open(Page.SCAN)
    }
}

/**
 * 앱 화면 본체. 액티비티 없이도 그릴 수 있게 분리해서, 화면 캡처 테스트가 실제 앱과 똑같은 구성을 그린다.
 */
@Composable
fun AppSurface(
    state: AppState,
    toast: ToastState,
    scanDialog: DialogSpec?,
    top: androidx.compose.ui.unit.Dp,
    bottom: androidx.compose.ui.unit.Dp,
    openScan: () -> Unit,
    scanScreen: @Composable () -> Unit,
    backdropEnabled: Boolean = true,       // 화면 캡처 테스트(소프트웨어 캔버스)에서는 끈다
) {
    val c = LocalColors.current
    var showPhoto by remember { mutableStateOf(false) }
    var schoolMenu by remember { mutableStateOf(false) }
    // 뒤 화면을 기록해 두는 레이어: 학교 선택 메뉴/토스트의 배경 흐림(backdrop blur)에 쓴다
    val backdropLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val backdrop = remember(backdropLayer) { Backdrop(backdropLayer) }
    val homeSc = androidx.compose.foundation.rememberScrollState()
    val cardSc = androidx.compose.foundation.rememberScrollState()
    val menuSc = androidx.compose.foundation.rememberScrollState()
    LaunchedEffect(state.tab, state.page) { schoolMenu = false }

    // 뒤로가기: 페이지 → 한 단계 닫기, 다른 탭 → 홈, 홈 → 종료(기본 동작)
    BackHandler(enabled = state.page != Page.NONE || state.tab != Tab.HOME) {
        if (state.page != Page.NONE) state.back() else state.tab = Tab.HOME
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalBottomInset provides bottom, LocalBackdrop provides backdrop, LocalBackdropEnabled provides backdropEnabled) {
    Box(Modifier.fillMaxSize().background(c.pageBg)) {
        PageHost(
            stack = state.stack, pageBg = c.pageBg,
            base = {
                Box(Modifier.fillMaxSize().background(c.pageBg)) {
                    // 뒤 화면(배경 + 탭 내용): 기록해 두었다가 탭바·상단 유리 버튼·토스트가 흐리게 비춘다. 이 상자 '바깥'의 요소는 기록되지 않는다.
                    Box(Modifier.fillMaxSize().recordBackdrop(backdrop).background(c.pageBg)) {
                        // 라이트 홈/학생증 탭은 템플릿의 그라데이션 배경, 다크모드는 같은 그라데이션을 어둡게 덮어 어두운 그라데이션으로
                        if (state.tab != Tab.MENU) {
                            Image(painterResource(if (state.tab == Tab.HOME) R.drawable.bg_home else R.drawable.bg_devices), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            if (c.dark) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xC2010102)))
                        }
                        Box(Modifier.fillMaxSize().padding(top = top)) {
                            when (state.tab) {
                                Tab.HOME -> HomeTab(state, onScan = openScan, onOpenCard = { state.tab = Tab.CARD }, onSoon = { toast.show("데모에서는 준비 중이에요") }, sc = homeSc)
                                Tab.CARD -> IdTab(state, onScan = openScan, onPhoto = { showPhoto = true }, sc = cardSc)
                                Tab.MENU -> MenuTab(onReRegister = openScan, onTips = { state.open(Page.TIPS) }, onInfo = { state.open(Page.INFO) }, sc = menuSc)
                            }
                        }
                    }
                    // 머리글(스크롤 내용 위에 떠 있음)과 탭바
                    Box(Modifier.fillMaxSize().padding(top = top)) {
                        when (state.tab) {
                            Tab.HOME -> Box(Modifier.fillMaxSize()) { SchoolTitle({ schoolMenu = true }, glass = homeSc.value > 0) }
                            Tab.CARD -> Box(Modifier.fillMaxSize()) { SchoolTitle({ schoolMenu = true }, glass = cardSc.value > 0) }
                            Tab.MENU -> MenuHeader(menuSc) { state.open(Page.SETTINGS) }
                        }
                    }
                    TabBar(state.tab, { state.tab = it }, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp + bottom))
                }
            },
            content = { pg ->
                Box(Modifier.fillMaxSize().padding(top = top)) {
                    when (pg) {
                        Page.SETTINGS -> SettingsPage(state, { state.back() }, onReRegister = openScan, onUnregister = {
                            if (state.registered) { state.unregister(); toast.show("등록된 학생증을 삭제했어요") } else toast.show("등록된 학생증이 없어요")
                        })
                        Page.TIPS -> TipsPage { state.back() }
                        Page.INFO -> InfoPage({ state.back() }, { state.open(Page.LICENSES) }, { state.open(Page.PRIVACY) })
                        Page.PRIVACY -> PrivacyPage { state.back() }
                        Page.LICENSES -> LicensesPage { state.back() }
                        Page.SCAN -> scanScreen()
                        Page.NONE -> {}
                    }
                }
            },
        )

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
        SchoolMenu(schoolMenu && state.page == Page.NONE && state.tab != Tab.MENU, top) { schoolMenu = false }
        DialogOverlay(scanDialog ?: zoom, bottom)
        ToastHost(toast, bottom)
    }
    }
}
