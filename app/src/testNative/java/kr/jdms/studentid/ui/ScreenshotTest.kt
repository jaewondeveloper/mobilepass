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
        rule.mainClock.autoAdvance = false          // 끝나지 않는 루프(시계 갱신 등)가 있어도 멈추지 않도록 수동 진행
        rule.mainClock.advanceTimeBy(2000)          // 화면 전환/애니메이션이 끝나기에 충분한 시간
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
