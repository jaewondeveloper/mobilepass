package kr.jdms.studentid.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
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
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @org.junit.Before fun setUp() { IdTabConfig.liveClock = false }

    private fun state(registered: Boolean): AppState {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = CardStore(app)
        if (registered) store.save("홍길동", "S1234567") else store.clear()
        return AppState(app, store)
    }

    /** 액티비티의 최상위 View 를 393x852dp 크기로 레이아웃하고 직접 비트맵에 그린다 */
    private fun drawActivityToBitmap(): Bitmap {
        val decor = rule.activity.window.decorView
        val dm = rule.activity.resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bmp))
        return bmp
    }

    private fun shot(name: String, dark: Boolean, registered: Boolean = true, setup: (AppState) -> Unit = {}) {
        val st = state(registered)
        setup(st)
        rule.setContent {
            AppTheme(dark) {
                AppSurface(st, ToastState(), null, 0.dp, 0.dp, openScan = {}, scanScreen = {}, backdropEnabled = false)
            }
        }
        rule.waitForIdle()                          // 시계 갱신 루프는 IdTabConfig.liveClock=false 로 꺼 두었으므로 idle 이 된다
        // captureToImage() 는 Robolectric 에서 PixelCopy 를 기다리다 시간 초과가 나므로, 화면(View)을 비트맵 캔버스에 직접 그린다
        val bmp = drawActivityToBitmap()
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

    private fun savePng(name: String) {
        val bmp = drawActivityToBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** 토스트 애니메이션: 가상 시계를 멈추고 원하는 시각(ms)으로 보내 가며 그려서 원본 HTML 의 같은 시각 모습과 비교한다 */
    @Test fun toast_frames() {
        val st = state(true)
        val toast = ToastState()
        rule.mainClock.autoAdvance = false
        rule.setContent { AppTheme(false) { AppSurface(st, toast, null, 0.dp, 0.dp, openScan = {}, scanScreen = {}, backdropEnabled = false) } }
        rule.runOnUiThread { toast.show("등록된 학생증을 삭제했어요") }
        rule.mainClock.advanceTimeByFrame()
        var t = 0
        for (ms in listOf(0, 100, 200, 300, 450, 620, 1000, 2500)) {
            if (ms > t) rule.mainClock.advanceTimeBy((ms - t).toLong())
            t = ms
            savePng("toast-$ms")
        }
    }

    /** 학교 선택 메뉴: 제목을 누른 뒤 열리는 중/열린 뒤 */
    @Test fun school_menu() {
        val st = state(true)
        rule.mainClock.autoAdvance = false
        rule.setContent { AppTheme(false) { AppSurface(st, ToastState(), null, 0.dp, 0.dp, openScan = {}, scanScreen = {}, backdropEnabled = false) } }
        rule.mainClock.advanceTimeBy(100)
        rule.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(70f, 34f)) }
        rule.mainClock.advanceTimeByFrame()
        var t = 0
        for (ms in listOf(60, 500)) {
            rule.mainClock.advanceTimeBy((ms - t).toLong()); t = ms
            savePng("menu-open-$ms")
        }
    }

    @Test fun home_light() = shot("home-light", false) { it.tab = Tab.HOME }
    @Test fun home_dark() = shot("home-dark", true) { it.tab = Tab.HOME }
    @Test fun home_empty_light() = shot("home-empty-light", false, registered = false) { it.tab = Tab.HOME }
    @Test fun card_light() = shot("devices-light", false) { it.tab = Tab.CARD }
    @Test fun card_dark() = shot("devices-dark", true) { it.tab = Tab.CARD }
    @Test fun menu_light() = shot("menu-light", false) { it.tab = Tab.MENU }
    @Test fun menu_dark() = shot("menu-dark", true) { it.tab = Tab.MENU }
    @Test fun settings_light() = shot("settings-light", false) { it.open(Page.SETTINGS) }
    @Test fun info_light() = shot("info-light", false) { it.open(Page.INFO) }
    @Test fun privacy_light() = shot("privacy-light", false) { it.open(Page.PRIVACY) }
    @Test fun licenses_light() = shot("licenses-light", false) { it.open(Page.LICENSES) }
    @Test fun settings_dark() = shot("settings-dark", true) { it.open(Page.SETTINGS) }
}
