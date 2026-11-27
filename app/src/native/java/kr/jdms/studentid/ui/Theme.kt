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
import androidx.compose.ui.unit.em
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
    val glassGlow: Color,     // 안쪽 번짐(inset) 색
    val btnBorder: Color,     // 카드 안 둥근 버튼의 1px 테두리
    val btnBg: Color,
    val body2: Color,         // 홈 '지금' 카드의 두 번째 줄 글자
    val menuDesc: Color,      // 메뉴 카드 설명
    val pageLabel: Color,     // '모바일 학생증' 작은 제목
    val tabLabel: Color,      // 하단바 비활성 글자/아이콘
    val line: Color,          // 목록 구분선
    val switchOn: Color,
    val switchKnob: Color,
)

val LightColors = AppColors(
    dark = false,
    pageBg = Color(0xFFF1F1F3), card = Color(0xFFFCFCFF), tile = Color(0x0A17171A),
    text = Color(0xFF010102), textSub = Color(0x99010102), textMuted = Color(0x73010102), textBody = Color(0xFF252528),
    divider = Color(0x14000000), accent = Color(0xFF387AFF), accentText = Color(0xFF2E65D4),
    tabBar = Color(0xD1FAFAFA), tabSelected = Color(0x17000000), tabIdle = Color(0xFF252528),
    pill = Color(0xFFE4E4E7), dialog = Color(0xFFFCFCFF), scrim = Color(0x66000000),
    glass = Color(0x4DFFFFFF), glassBorder = Color(0xFFFFFFFF), switchOff = Color(0xFF999999),
    glassGlow = Color(0xFFFFFFFF), btnBorder = Color(0xFFE4E4E7), btnBg = Color(0xFFFCFCFF),
    body2 = Color(0xFF4D4D52), menuDesc = Color(0xFF252528), pageLabel = Color(0xFF636368), tabLabel = Color(0xFF252528),
    line = Color(0xFFE4E4E7), switchOn = Color(0xFF387AFF), switchKnob = Color(0xFFFCFCFF),
)

val DarkColors = AppColors(
    dark = true,
    pageBg = Color(0xFF010102), card = Color(0xFF17171A), tile = Color(0x0DFCFCFF),
    text = Color(0xFFFCFCFF), textSub = Color(0x99FCFCFF), textMuted = Color(0x73FAFAFF), textBody = Color(0xFFE9E9EC),
    divider = Color(0x1AFFFFFF), accent = Color(0xFF387AFF), accentText = Color(0xFF598FFF),
    tabBar = Color(0xFF252527), tabSelected = Color(0xFF3B3B3D), tabIdle = Color(0xFFE9E9EC),
    pill = Color(0xFF3A3A3D), dialog = Color(0xFF3A3A3C), scrim = Color(0x99000000),
    glass = Color(0x4D1C1C1C), glassBorder = Color(0xFF222222), switchOff = Color(0xFF666660),
    glassGlow = Color(0xB30E0E0E), btnBorder = Color(0xFF3A3A3D), btnBg = Color(0xFF010102),
    body2 = Color(0xFFCCCCCE), menuDesc = Color(0xFF99999E), pageLabel = Color(0xFF636368), tabLabel = Color(0xFFE9E9EC),
    line = Color(0xFF3A3A3D), switchOn = Color(0xFF387AFF), switchKnob = Color(0xFFFCFCFF),
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
    ) {
        // 원본 HTML 의 line-height:normal 과 같은 줄 높이(≈1.2배)를 글자 크기와 상관없이 고정해, 글자 상자 높이/위치가 기기 글꼴과 무관하게 같도록 한다
        androidx.compose.material3.ProvideTextStyle(
            androidx.compose.ui.text.TextStyle(
                lineHeight = 1.2f.em,
                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                    androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                    androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
                ),
            ),
            content,
        )
    }
}

val Int.fsp: TextUnit get() = TextUnit(this.toFloat(), TextUnitType.Sp)
