package kr.jdms.studentid.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateDpAsState
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

/** 홈의 '지금' 카드처럼 배경 그라데이션 위에 얹는 유리 느낌 카드 */
fun Modifier.glass(c: AppColors, shape: RoundedCornerShape = RoundedCornerShape(30.dp)): Modifier =
    this.clip(shape)
        .background(c.glass)
        .let {
            if (c.dark) it else it.background(Brush.verticalGradient(listOf(Color(0x99FFFFFF), Color(0x26FFFFFF), Color(0x80FFFFFF))))
        }
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
) {
    Row(
        modifier.height(height).clip(RoundedCornerShape(height / 2)).background(bg).clickable(onClick = onClick).padding(horizontal = 22.dp),
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
        modifier.width(250.dp).height(56.dp).clip(RoundedCornerShape(28.dp)).background(c.tabBar).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (tab, icon, label) ->
            val on = tab == selected
            Column(
                Modifier.width(80.dp).height(48.dp).clip(RoundedCornerShape(24.dp))
                    .background(if (on) c.tabSelected else Color.Transparent)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(tab) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Ico(icon, 22.dp, tint = if (on) c.text else c.tabIdle)
                Text(label, color = if (on) c.text else c.tabIdle, fontSize = 10.fsp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
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

// ───────────────────────── 템플릿 Dialog 모양 (하단 시트) ─────────────────────────

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
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = spec != null, enter = fadeIn(tween(250)), exit = fadeOut(tween(200))) {
            Box(
                Modifier.fillMaxSize().background(c.scrim)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { spec?.let { if (it.cancelable) it.onCancel() } },
            )
        }
        AnimatedVisibility(
            visible = spec != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(350)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(250)) { it } + fadeOut(tween(200)),
        ) {
            val s = last
            if (s != null) {
                Column(
                    Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp + bottomInset).fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp)).background(c.dialog)
                        .pointerInput(Unit) { detectTapGestures { } },
                ) {
                    Text(s.title, color = c.text, fontSize = 20.fsp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp))
                    if (s.message != null) Text(s.message, color = c.textSub, fontSize = 15.fsp, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp))
                    if (s.content != null) Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 14.dp)) { s.content.invoke() }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
                        s.actions.forEachIndexed { i, a ->
                            if (i > 0) Box(Modifier.width(1.dp).height(24.dp).background(c.divider))
                            Box(Modifier.weight(1f).fillMaxSize().clickable(onClick = a.onClick), contentAlignment = Alignment.Center) {
                                Text(a.label, color = c.text, fontSize = 17.fsp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
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

@Composable
fun ToastHost(state: ToastState, bottomInset: Dp) {
    val msg = state.message
    LaunchedEffect(msg) { if (msg != null) { delay(2400); if (state.message == msg) state.message = null } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible = msg != null, enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { it / 2 }, exit = fadeOut(tween(250))) {
            Box(
                Modifier.padding(bottom = 120.dp + bottomInset).height(44.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xB81E2026)).padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) { Text(msg ?: "", color = Color.White, fontSize = 14.fsp, fontWeight = FontWeight.Medium) }
        }
    }
}
