package kr.jdms.studentid.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.jdms.studentid.R

@Composable
fun Ico(@DrawableRes id: Int, size: Dp, tint: Color? = null, modifier: Modifier = Modifier) {
    Image(painterResource(id), null, modifier.size(size), colorFilter = tint?.let { ColorFilter.tint(it) })
}

val CardShape = RoundedCornerShape(28.dp)

/** 홈의 '지금' 카드: 반투명 배경 + 1px 테두리 + 바깥 그림자 + 안쪽 번짐 (원본 box-shadow: 0 0 3px #0000000d, 0 0 50px 9px inset) */
fun Modifier.glass(c: AppColors, shape: RoundedCornerShape = RoundedCornerShape(30.dp)): Modifier =
    this.cssShadow(30.dp, 3.dp, 0.dp, Color(0x0D000000))
        .clip(shape)
        .background(c.glass)
        .cssInsetGlow(30.dp, 50.dp, 9.dp, c.glassGlow)
        .border(1.dp, c.glassBorder, shape)

// ───────────────────────── 둥근 버튼 (원본 wx-button: 누르면 0.985 로 줄고 물결이 깔림) ─────────────────────────

@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    bg: Color = LocalColors.current.pill,
    fg: Color = LocalColors.current.text,
    fontSize: Int = 17,
    icon: Int? = null,
    border: Color? = null,
    scale: Float = 0.985f,
) {
    val shape = RoundedCornerShape(height / 2)
    PressBox(
        modifier.height(height), shape = shape, scale = scale, bg = bg, border = border ?: Color.Unspecified,
        contentAlignment = Alignment.Center, onClick = onClick,
    ) {
        if (icon != null) {
            Row(Modifier.padding(start = 8.dp, end = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                Ico(icon, 26.dp); Spacer(Modifier.width(2.dp))
                T(label, fontSize, fg, 600, maxLines = 1, softWrap = false)
            }
        } else {
            T(label, fontSize, fg, 600, maxLines = 1, softWrap = false, modifier = Modifier.padding(horizontal = 22.dp))
        }
    }
}

// ───────────────────────── 하단 탭바 (250x56, 알약 86x48 이 78 간격으로 겹침) ─────────────────────────

@Composable
fun TabBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    class Item(val tab: Tab, val off: Int, val on: Int, val label: String)
    val items = listOf(
        Item(Tab.HOME, R.drawable.ic_tab_home_off, R.drawable.ic_tab_home_on, "홈"),
        Item(Tab.CARD, R.drawable.ic_tab_card_off, R.drawable.ic_tab_card_on, "학생증"),
        Item(Tab.MENU, R.drawable.ic_tab_menu_off, R.drawable.ic_tab_menu_on, "메뉴"),
    )
    Box(
        modifier.width(250.dp).height(56.dp)
            .cssShadow(28.dp, 12.dp, 2.5.dp, if (c.dark) Color(0x59000000) else Color(0x1A000000))
            .cssShadow(28.dp, 4.dp, 0.dp, if (c.dark) Color(0x1A000000) else Color(0x0A000000))
            .clip(RoundedCornerShape(28.dp)).background(c.tabBar),
    ) {
        items.forEachIndexed { i, it ->
            val on = it.tab == selected
            val tint by animateColorAsState(if (on) c.text else c.tabLabel, tween(50, easing = LinearEasing), label = "tabTint")
            PressBox(
                Modifier.align(Alignment.TopStart).offset((4 + 78 * i).dp, 4.dp).size(86.dp, 48.dp),
                shape = RoundedCornerShape(24.dp), scale = 0.965f,
                bg = if (on) c.tabSelected else Color.Unspecified,
                onClick = { onSelect(it.tab) },
            ) {
                Ico(if (on) it.on else it.off, 24.dp, tint = tint, modifier = Modifier.align(Alignment.TopCenter).offset(y = 5.dp))
                T(it.label, 10, tint, if (on) 600 else 400, align = TextAlign.Center, softWrap = false, modifier = Modifier.align(Alignment.TopCenter).offset(y = 29.dp))
            }
        }
    }
}

// ───────────────────────── 상단 아이콘 버튼 / 뒤로가기 머리글 ─────────────────────────

/** 상단 바의 아이콘 버튼: 48x48 원형 물결, 누르면 0.925 로 줄어듦 (원본 wx-action-bar-button) */
@Composable
fun ActionIcon(@DrawableRes icon: Int, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalColors.current
    PressBox(modifier.size(48.dp), shape = CircleShape, scale = 0.925f, contentAlignment = Alignment.Center, onClick = onClick) {
        Ico(icon, 24.dp, tint = c.text)
    }
}

/** 뒤로가기 머리글: 버튼은 (12,12), 제목은 x=60, 머리글 높이 68 */
@Composable
fun BackHeader(title: String, onBack: () -> Unit) {
    val c = LocalColors.current
    Box(Modifier.fillMaxWidth().height(68.dp)) {
        ActionIcon(R.drawable.ic_back, Modifier.align(Alignment.TopStart).offset(12.dp, 12.dp), onBack)
        PT(title, 60f, 23.5f, 20, c.text, 700)
    }
}

// ───────────────────────── 목록 카드 / 행 / 스위치 ─────────────────────────

@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = LocalColors.current
    Column(modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(22.dp)).background(c.card)) { content() }
}

/**
 * 설정/목록 행. 높이는 설명이 있으면 64, 없으면 52. 눌리면 내용이 0.992 로 줄고 물결이 깔린다.
 * 제목 17/23, 설명 12/16, 구분선은 행 맨 아래 1px (좌우 16 안쪽).
 */
@Composable
fun ListRow(
    title: String,
    desc: String? = null,
    divider: Boolean = false,
    descAccent: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = LocalColors.current
    PressBox(
        Modifier.fillMaxWidth(),
        innerScale = 0.992f, rippleLight = 0.1f, rippleDark = 0.16f, onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (desc != null) 64.dp else 52.dp).padding(start = 18.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(top = if (desc != null) 11.5.dp else 14.dp, bottom = if (desc != null) 11.5.dp else 14.dp)) {
                T(title, 17, c.text, 400, lh = 23)
                if (desc != null) {
                    Spacer(Modifier.height(1.dp))
                    T(desc, if (descAccent) 12 else 14, if (descAccent) c.accentText else c.textSub, 400, lh = if (descAccent) 16 else 19)
                }
            }
            trailing?.invoke()
        }
        if (divider) Box(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(c.line))
    }
}

/**
 * 원본 wx-toggle-switch: 누르는 영역 43x27, 트랙 35x20(둥근 10), 손잡이 16.
 * 트랙 색 200ms cubic-bezier(.8,0,.22,1), 손잡이 이동 200ms cubic-bezier(.4,0,.6,1),
 * 누르는 동안 트랙·손잡이가 왼쪽으로 1.1 밀리며 0.992 로 줄어든다.
 */
@Composable
fun AppSwitch(checked: Boolean) {
    val c = LocalColors.current
    val pressed = LocalPressed.current
    val track by animateColorAsState(if (checked) c.switchOn else c.switchOff, tween(200, easing = CubicBezierEasing(0.8f, 0f, 0.22f, 1f)), label = "swTrack")
    val knob by animateDpAsState(if (checked) 17.dp else 2.dp, tween(200, easing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)), label = "swKnob")
    val p by animateFloatAsState(if (pressed) 1f else 0f, tween(100, easing = LinearEasing), label = "swPress")
    Box(Modifier.size(43.dp, 27.dp)) {
        Box(
            Modifier.align(Alignment.TopStart).offset(4.dp, 3.5.dp).size(35.dp, 20.dp)
                .graphicsLayer {
                    translationX = -1.1f * p * density
                    scaleX = 1f - 0.008f * p; scaleY = 1f - 0.008f * p
                }
                .clip(RoundedCornerShape(10.dp)).background(track),
        ) {
            Box(Modifier.offset(knob, 2.dp).size(16.dp).clip(CircleShape).background(c.switchKnob))
        }
    }
}
