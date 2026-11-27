package kr.jdms.studentid.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.test.core.app.ApplicationProvider
import kr.jdms.studentid.CardStore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 네이티브 화면을 393x852dp(웹 렌더링과 같은 크기)로 그려서 PNG 로 저장한다.
 * 결과는 app/build/screenshots/ 에 쌓이고 CI 가 올려 주므로, 원본 HTML 스크린샷과 나란히 비교할 수 있다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-mdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @org.junit.Before fun setUp() { IdTabConfig.liveClock = false }

    private fun state(registered: Boolean): AppState {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = CardStore(app)
        if (registered) store.save("홍길동", "S1234567") else store.clear()
        return AppState(app, store)
    }

    private fun shot(name: String, dark: Boolean, registered: Boolean = true, setup: (AppState) -> Unit = {}) {
        val st = state(registered)
        setup(st)
        rule.setContent {
            AppTheme(dark) {
                AppSurface(st, ToastState(), null, 0.dp, 0.dp, openScan = {}, scanScreen = {})
            }
        }
        rule.waitForIdle()                          // 시계 갱신 루프는 IdTabConfig.liveClock=false 로 꺼 두었으므로 idle 이 된다
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        // 글자 위치/크기를 JSON 으로: 원본 HTML 의 같은 화면 수치(ref/*.json)와 숫자로 비교하기 위함
        val nodes = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
        val sb = StringBuilder("{\"texts\":[")
        nodes.forEachIndexed { i, n ->
            val t = n.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty().replace("\\", "\\\\").replace("\"", "\\\"")
            val b = n.boundsInRoot
            if (i > 0) sb.append(",")
            sb.append("{\"t\":\"$t\",\"x\":${b.left},\"y\":${b.top},\"w\":${b.width},\"h\":${b.height}}")
        }
        sb.append("]}")
        File(dir, "$name.json").writeText(sb.toString())
    }

    // ── 진단: idle 을 막는 요소 찾기 (통과/실패 조합으로 범인을 가린다) ──
    @Test fun diag_a_plain_text() {
        rule.setContent { androidx.compose.material3.Text("hello") }
        rule.waitForIdle()
    }
    @Test fun diag_b_theme_only() {
        rule.setContent { AppTheme(false) { androidx.compose.material3.Text("hello") } }
        rule.waitForIdle()
    }
    @Test fun diag_c_home_tab_only() {
        val st = state(true)
        rule.setContent { AppTheme(false) { HomeTab(st, {}, {}, {}) } }
        rule.waitForIdle()
    }
    @Test fun diag_d_pagehost_only() {
        val st = state(true)
        rule.setContent { AppTheme(false) { PageHost(st.stack, base = { androidx.compose.material3.Text("base") }, pageBg = androidx.compose.ui.graphics.Color.White, content = {}) } }
        rule.waitForIdle()
    }
    @Test fun diag_e_dialog_only() {
        rule.setContent { AppTheme(false) { DialogOverlay(null, 0.dp) } }
        rule.waitForIdle()
    }
    @Test fun diag_f_toast_only() {
        rule.setContent { AppTheme(false) { ToastHost(ToastState(), 0.dp) } }
        rule.waitForIdle()
    }
    @Test fun diag_g_tabbar_only() {
        rule.setContent { AppTheme(false) { TabBar(Tab.HOME, {}) } }
        rule.waitForIdle()
    }

    // ── 진단 2: AppSurface 를 구성요소 단위로 쌓아 가며 처음 실패하는 조합을 찾는다 ──
    private fun idle(content: @androidx.compose.runtime.Composable () -> Unit) { rule.setContent { AppTheme(false) { content() } }; rule.waitForIdle() }
    @Test fun diag_h_pagehost_with_home() { val st = state(true); idle { PageHost(st.stack, base = { HomeTab(st, {}, {}, {}) }, pageBg = androidx.compose.ui.graphics.Color.White, content = {}) } }
    @Test fun diag_i_pagehost_with_home_and_tabbar() { val st = state(true); idle { PageHost(st.stack, base = { androidx.compose.foundation.layout.Box { HomeTab(st, {}, {}, {}); TabBar(Tab.HOME, {}) } }, pageBg = androidx.compose.ui.graphics.Color.White, content = {}) } }
    @Test fun diag_j_image_bg() { idle { androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(kr.jdms.studentid.R.drawable.bg_home), null) } }
    @Test fun diag_k_backhandler() { idle { androidx.activity.compose.BackHandler(enabled = true) {}; androidx.compose.material3.Text("x") } }
    @Test fun diag_l_boxwithconstraints() { idle { androidx.compose.foundation.layout.BoxWithConstraints { androidx.compose.material3.Text("${maxWidth}") } } }
    @Test fun diag_m_surface_no_dialog_toast() {
        val st = state(true)
        idle { val c = LocalColors.current; androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(c.pageBg)) { PageHost(st.stack, base = { HomeTab(st, {}, {}, {}) }, pageBg = c.pageBg, content = {}) } }
    }
    @Test fun diag_n_full_surface_dark_no_image() { val st = state(true); idle { AppSurface(st, ToastState(), null, 0.dp, 0.dp, {}, {}) } }

    @Test fun home_light() = shot("home-light", false) { it.tab = Tab.HOME }
    @Test fun home_dark() = shot("home-dark", true) { it.tab = Tab.HOME }
    @Test fun home_empty_light() = shot("home-empty-light", false, registered = false) { it.tab = Tab.HOME }
    @Test fun card_light() = shot("devices-light", false) { it.tab = Tab.CARD }
    @Test fun card_dark() = shot("devices-dark", true) { it.tab = Tab.CARD }
    @Test fun menu_light() = shot("menu-light", false) { it.tab = Tab.MENU }
    @Test fun menu_dark() = shot("menu-dark", true) { it.tab = Tab.MENU }
    @Test fun settings_light() = shot("settings-light", false) { it.open(Page.SETTINGS) }
    @Test fun settings_dark() = shot("settings-dark", true) { it.open(Page.SETTINGS) }
}
