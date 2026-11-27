package kr.jdms.studentid.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType

/**
 * HTML 템플릿(SmartThings Virtual Home)의 색. 웹 화면을 렌더링한 픽셀/계산 스타일에서 뽑은 값이다.
 */
data class AppColors(
    val dark: Boolean,
    val pageBg: Color,        // 메뉴·설정 페이지의 평면 배경
    val card: Color,
    val tile: Color,
    val text: Color,
    val textSub: Color,       // 60%
    val textMuted: Color,     // 45%
    val textBody: Color,      // 카드 안 본문
    val divider: Color,
    val accent: Color,        // 스위치/강조 파랑
    val accentText: Color,    // 설정 항목 설명 글자
    val tabBar: Color,
    val tabSelected: Color,
    val tabIdle: Color,
    val pill: Color,          // '학생증 다시 등록하기' 같은 둥근 버튼
    val dialog: Color,
    val scrim: Color,
    val glass: Color,
    val glassBorder: Color,
    val switchOff: Color,
)

val LightColors = AppColors(
    dark = false,
    pageBg = Color(0xFFF1F1F3), card = Color(0xFFFCFCFF), tile = Color(0xFFF3F3F6),
    text = Color(0xFF010102), textSub = Color(0x99010102), textMuted = Color(0x73010102), textBody = Color(0xFF252528),
    divider = Color(0x14000000), accent = Color(0xFF387AFF), accentText = Color(0xFF2E65D4),
    tabBar = Color(0xD1FAFAFA), tabSelected = Color(0x1A000000), tabIdle = Color(0xFF848487),
    pill = Color(0xFFE6E6E9), dialog = Color(0xFFFCFCFF), scrim = Color(0x66000000),
    glass = Color(0x66FFFFFF), glassBorder = Color(0xCCFFFFFF), switchOff = Color(0xFF9A9A9D),
)

val DarkColors = AppColors(
    dark = true,
    pageBg = Color(0xFF010102), card = Color(0xFF17171A), tile = Color(0xFF222225),
    text = Color(0xFFFAFAFA), textSub = Color(0x99FAFAFA), textMuted = Color(0x73FAFAFA), textBody = Color(0xFFD9D9DC),
    divider = Color(0x1AFFFFFF), accent = Color(0xFF3E7DFF), accentText = Color(0xFF7FA6FF),
    tabBar = Color(0xE62D2D2D), tabSelected = Color(0xFF424242), tabIdle = Color(0xFF9A9A9D),
    pill = Color(0xFF3A3A3D), dialog = Color(0xFF3A3A3C), scrim = Color(0x99000000),
    glass = Color(0xFF080809), glassBorder = Color(0x1FFFFFFF), switchOff = Color(0xFF5A5A5D),
)

val LocalColors = staticCompositionLocalOf { LightColors }

/** 웹 화면의 px 를 그대로 dp/sp 로 쓰므로 글자 크기가 시스템 설정으로 변하지 않게 고정한다 (웹의 textZoom = 100 과 같음) */
@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    val colors = if (dark) DarkColors else LightColors
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalDensity provides Density(d.density, fontScale = 1f),
        LocalIndication provides ripple(color = colors.text),      // 카드/타일을 누를 때의 물결 (wx-ripple)
        content = content,
    )
}

val Int.fsp: TextUnit get() = TextUnit(this.toFloat(), TextUnitType.Sp)
