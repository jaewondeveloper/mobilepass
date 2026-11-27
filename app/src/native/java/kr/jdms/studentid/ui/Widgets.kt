package kr.jdms.studentid.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
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

// ───────────────────────── 둥근 버튼 ─────────────────────────

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
) {
    val shape = RoundedCornerShape(height / 2)
    Row(
        modifier.recoilClickable(scale = 0.95f, onClick = onClick).height(height).clip(shape).background(bg)
            .let { if (border != null) it.border(1.dp, border, shape) else it }.padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Ico(icon, 26.dp); Spacer(Modifier.width(6.dp)) }
        Text(label, color = fg, fontSize = fontSize.fsp, fontWeight = FontWeight.SemiBold)
    }
}

// ───────────────────────── 하단 탭바 ─────────────────────────

@Composable
fun TabBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val items = listOf(Triple(Tab.HOME, R.drawable.ic_tab_home, "홈"), Triple(Tab.CARD, R.drawable.ic_tab_card, "학생증"), Triple(Tab.MENU, R.drawable.ic_tab_menu, "메뉴"))
    Row(
        modifier.width(250.dp).height(56.dp)
            .cssShadow(28.dp, 12.dp, 2.5.dp, if (c.dark) Color(0x59000000) else Color(0x1A000000))
            .cssShadow(28.dp, 4.dp, 0.dp, if (c.dark) Color(0x1A000000) else Color(0x0A000000))
            .clip(RoundedCornerShape(28.dp)).background(c.tabBar).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (tab, icon, label) ->
            val on = tab == selected
            val tint by animateColorAsState(if (on) c.text else c.tabIdle, tween(100, easing = LinearEasing), label = "tabTint")
            Column(
                Modifier.recoilClickable(scale = 0.965f) { onSelect(tab) }
                    .width(80.dp).height(48.dp).clip(RoundedCornerShape(24.dp))
                    .background(if (on) c.tabSelected else Color.Transparent),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Ico(icon, 22.dp, tint = tint)
                Text(label, color = tint, fontSize = 10.fsp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

// ───────────────────────── 페이지 머리글 (뒤로가기 + 제목) ─────────────────────────

@Composable
fun BackHeader(title: String, onBack: () -> Unit) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(start = 12.dp).size(48.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Ico(R.drawable.ic_back, 24.dp, tint = c.text)
        }
        Text(title, color = c.text, fontSize = 20.fsp, fontWeight = FontWeight.Bold)
    }
}

// ───────────────────────── 목록 카드 ─────────────────────────

@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = LocalColors.current
    Column(modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(CardShape).background(c.card)) { content() }
}

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
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (desc != null) 64.dp else 52.dp).let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = c.text, fontSize = 17.fsp)
                if (desc != null) Text(desc, color = if (descAccent) c.accentText else c.textSub, fontSize = if (descAccent) 12.fsp else 14.fsp)
            }
            trailing?.invoke()
        }
        if (divider) Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(1.dp).background(c.divider))
    }
}

@Composable
fun AppSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalColors.current
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, tween(160), label = "sw")
    Box(
        Modifier.size(51.dp, 31.dp).clip(RoundedCornerShape(16.dp)).background(if (checked) c.accent else c.switchOff)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(!checked) },
    ) {
        Box(Modifier.offset(x = x, y = 2.dp).size(27.dp).clip(CircleShape).background(Color.White))
    }
}

// ───────────────────────── 템플릿 Dialog (측정값: 반지름 26, 안쪽 여백 24, 제목 17sp, 버튼 18sp) ─────────────────────────

class DialogAction(val label: String, val onClick: () -> Unit)

class DialogSpec(
    val title: String,
    val message: String? = null,
    val content: (@Composable () -> Unit)? = null,
    val actions: List<DialogAction>,
    val cancelable: Boolean = true,
    val onCancel: () -> Unit = {},
)

@Composable
fun DialogOverlay(spec: DialogSpec?, bottomInset: Dp) {
    val c = LocalColors.current
    var last by remember { mutableStateOf<DialogSpec?>(null) }
    if (spec != null) last = spec
    BackHandler(enabled = spec != null) { if (spec != null && spec.cancelable) spec.onCancel() }
    val slide = with(androidx.compose.ui.platform.LocalDensity.current) { 100.dp.roundToPx() }
    Box(Modifier.fillMaxSize()) {
        // 배경 어둡게: 열 때 150ms linear, 닫을 때 250ms(67ms 지연) linear
        AnimatedVisibility(
            visible = spec != null,
            enter = fadeIn(tween(150, easing = LinearEasing)),
            exit = fadeOut(tween(250, delayMillis = 67, easing = LinearEasing)),
        ) {
            Box(
                Modifier.fillMaxSize().background(c.scrim)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { spec?.let { if (it.cancelable) it.onCancel() } },
            )
        }
        // 시트: 아래 100px 에서 300ms cubic-bezier(.17,.17,.2,1) (열 때 100ms 지연), 페이드 150ms(열기) / 100ms(닫기)
        AnimatedVisibility(
            visible = spec != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(300, delayMillis = 100, easing = Motion.Dialog)) { slide } + fadeIn(tween(150, delayMillis = 100, easing = Motion.Ease)),
            exit = slideOutVertically(tween(300, easing = Motion.Dialog)) { slide } + fadeOut(tween(100, easing = LinearEasing)),
        ) {
            val s = last
            if (s != null) {
                val shape = RoundedCornerShape(26.dp)
                Column(
                    Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp + bottomInset).fillMaxWidth()
                        .cssShadow(26.dp, 8.dp, 2.dp, Color(0x26000000))
                        .cssShadow(26.dp, 2.dp, 0.dp, Color(0x0A000000))
                        .clip(shape).background(c.dialog)
                        .pointerInput(Unit) { detectTapGestures { } }
                        .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 20.dp),
                ) {
                    Text(s.title, color = c.text, fontSize = 17.fsp, lineHeight = 23.fsp, fontWeight = FontWeight.SemiBold)
                    if (s.message != null) Text(s.message, color = c.textSub, fontSize = 15.fsp, modifier = Modifier.padding(top = 12.dp))
                    if (s.content != null) Box(Modifier.fillMaxWidth().padding(top = 12.dp)) { s.content.invoke() }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                        s.actions.forEachIndexed { i, a ->
                            if (i > 0) Box(Modifier.width(1.dp).height(16.dp).background(Color(0x4D808080)))
                            Box(Modifier.weight(1f).fillMaxSize().recoilClickable(scale = 0.96f, onClick = a.onClick), contentAlignment = Alignment.Center) {
                                Text(a.label, color = c.text, fontSize = 18.fsp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── 토스트 ─────────────────────────

class ToastState {
    var message by mutableStateOf<String?>(null)
    fun show(msg: String) { message = msg }
}

/**
 * 원 모양으로 올라오며 커지다가 좌우로 늘어나며 글자가 나타나는 토스트 (원본 CSS 와 같은 620ms 키프레임).
 * 2.4초 뒤 250ms 동안 사라진다. 위치: 아래에서 120px + 시스템 바.
 */
@Composable
fun ToastHost(state: ToastState, bottomInset: Dp) {
    val msg = state.message
    val shown = remember { mutableStateOf<String?>(null) }
    val progress = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(msg) {
        if (msg == null) return@LaunchedEffect
        shown.value = msg
        alpha.snapTo(1f)
        progress.snapTo(0f)
        progress.animateTo(1f, tween(620, easing = LinearEasing))
        delay(2400 - 620L)
        if (state.message == msg) {
            alpha.animateTo(0f, tween(250, easing = Motion.Ease))
            if (state.message == msg) state.message = null
        }
    }
    val text = shown.value ?: return
    if (alpha.value <= 0f && state.message == null) return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        val t = progress.value
        val e1 = CubicBezierEasing(0.2f, 0.8f, 0.3f, 1f)
        val e2 = CubicBezierEasing(0.3f, 0.7f, 0.3f, 1f)
        val rise = if (t < 0.38f) e1.transform(t / 0.38f) else 1f
        val grow = if (t < 0.38f) 0f else e2.transform((t - 0.38f) / 0.62f)
        val textAlpha = if (t < 0.55f) 0f else ((t - 0.55f) / 0.45f).coerceIn(0f, 1f)
        val density = androidx.compose.ui.platform.LocalDensity.current
        Box(
            Modifier.padding(bottom = 120.dp + bottomInset)
                .graphicsLayer {
                    this.alpha = alpha.value
                    val sc = 0.15f + 0.85f * rise
                    scaleX = sc; scaleY = sc
                    translationY = with(density) { (80.dp * (1f - rise)).toPx() }
                }
                .layout { m, c ->
                    val p = m.measure(c.copy(minWidth = 0))
                    val d = 44.dp.roundToPx()
                    val w = (d + (p.width - d) * grow).toInt().coerceAtLeast(d)
                    layout(w, p.height) { p.placeRelative((w - p.width) / 2, 0) }
                }
                .clip(RoundedCornerShape(22.dp)).background(Color(0xB81E2026)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text, color = Color.White.copy(alpha = textAlpha), fontSize = 14.fsp, fontWeight = FontWeight.Medium, maxLines = 1,
                modifier = Modifier.height(44.dp).padding(horizontal = 20.dp).wrapContentHeight(Alignment.CenterVertically),
            )
        }
    }
}
