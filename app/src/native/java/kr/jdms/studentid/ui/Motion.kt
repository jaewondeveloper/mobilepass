package kr.jdms.studentid.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * HTML 템플릿의 모션을 Web Animations API 로 측정한 값.
 *  - 누를 때 줄었다 돌아오는 'recoil': transform 350ms cubic-bezier(.22,.25,0,1), 눌린 크기 ≈ 0.96
 *  - 페이지 열기: 새 페이지 오른쪽에서 450ms cubic-bezier(.22,.5,0,1), 뒤 페이지는 화면 폭의 1/3 만큼 밀리며 밝기 0.8 (150ms linear)
 *  - 페이지 닫기: 400ms cubic-bezier(.22,.25,0,1), 뒤 페이지 밝기 복귀 250ms linear
 *  - 다이얼로그: 100px 아래에서 300ms cubic-bezier(.17,.17,.2,1) (열 때 100ms 지연), 페이드 150ms / 닫을 때 100ms
 */
object Motion {
    val Recoil = CubicBezierEasing(0.22f, 0.25f, 0f, 1f)
    val PageIn = CubicBezierEasing(0.22f, 0.5f, 0f, 1f)
    val PageOut = CubicBezierEasing(0.22f, 0.25f, 0f, 1f)
    val Dialog = CubicBezierEasing(0.17f, 0.17f, 0.2f, 1f)
    val Ease = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    val RippleOut = CubicBezierEasing(0.17f, 0.17f, 0.67f, 1f)
    const val RECOIL_MS = 350
    const val PAGE_IN_MS = 450
    const val PAGE_OUT_MS = 400
    const val PAGE_PARALLAX = 1f / 3f
    const val PAGE_DIM = 0.2f          // brightness(0.8)
}

/** 아주 빨리 탭해도 눌림 효과가 눈에 보이게 하는 최소 유지 시간 (원본은 눌림 전환이 항상 한 번은 재생된다) */
const val MIN_PRESS_MS = 130L

/** 누름 상태: down = 손가락이 닿아 있음, shown = 화면에 보이는 눌림(닿아 있거나 최소 유지 시간 안) */
class PressHold {
    var down by mutableStateOf(false)
    var shown by mutableStateOf(false)
    var since = 0L
}

@Composable
fun rememberPressHold(): PressHold {
    val h = remember { PressHold() }
    LaunchedEffect(h.down) {
        if (h.down) {
            h.since = android.os.SystemClock.uptimeMillis()
            h.shown = true
        } else if (h.shown) {
            val rest = MIN_PRESS_MS - (android.os.SystemClock.uptimeMillis() - h.since)
            if (rest > 0) kotlinx.coroutines.delay(rest)
            h.shown = false
        }
    }
    return h
}

/**
 * 탭 처리: 손가락이 닿는 순간(지연 없이) 눌림이 시작된다.
 * (기본 clickable 은 빠른 탭에서 눌림 표시를 건너뛰어서 아무 반응이 없어 보였다)
 */
@Composable
fun Modifier.pressTap(hold: PressHold, enabled: Boolean = true, onClick: (() -> Unit)?): Modifier {
    if (onClick == null) return this
    val current = androidx.compose.runtime.rememberUpdatedState(onClick)
    return this
        .pointerInput(enabled) {
            detectTapGestures(
                onPress = {
                    if (enabled) {
                        hold.down = true
                        try { tryAwaitRelease() } finally { hold.down = false }
                    }
                },
                onTap = { if (enabled) current.value() },
            )
        }
        .semantics { role = androidx.compose.ui.semantics.Role.Button; this.onClick { current.value(); true } }
}

/** 누르는 동안 살짝 줄어들었다가 떼면 부드럽게 돌아오는 템플릿 버튼의 눌림 효과 + 클릭 */
@Composable
fun Modifier.recoilClickable(scale: Float = 0.96f, enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val hold = rememberPressHold()
    val s by animateFloatAsState(if (hold.shown) scale else 1f, tween(Motion.RECOIL_MS, easing = Motion.Recoil), label = "recoil")
    return this.graphicsLayer { scaleX = s; scaleY = s }.pressTap(hold, enabled, onClick)
}

private class Layer(val page: Page) {
    val progress = Animatable(0f)
    val dim = Animatable(0f)
    var exiting by mutableStateOf(false)
}

/**
 * 페이지 스택 전환: 새 페이지가 오른쪽에서 밀려 들어오고 아래 화면은 1/3 만큼 밀리며 어두워진다. 닫을 땐 반대.
 * base = 탭 화면(탭바 포함). 닫히는 중인 페이지는 애니메이션이 끝날 때까지 그대로 그린다.
 */
@Composable
fun PageHost(stack: List<Page>, base: @Composable () -> Unit, pageBg: Color, content: @Composable (Page) -> Unit) {
    val layers = remember { mutableStateListOf<Layer>() }
    val scope = rememberCoroutineScope()
    val snapshot = stack.toList()
    LaunchedEffect(snapshot) {
        layers.filter { !it.exiting && it.page !in snapshot }.forEach { l ->
            l.exiting = true
            scope.launch { l.dim.animateTo(0f, tween(250, easing = LinearEasing)) }
            scope.launch { l.progress.animateTo(0f, tween(Motion.PAGE_OUT_MS, easing = Motion.PageOut)); layers.remove(l) }
        }
        snapshot.forEach { pg ->
            if (layers.none { it.page == pg && !it.exiting }) {
                val l = Layer(pg)
                layers.add(l)
                scope.launch { l.progress.animateTo(1f, tween(Motion.PAGE_IN_MS, easing = Motion.PageIn)) }
                scope.launch { l.dim.animateTo(1f, tween(150, easing = LinearEasing)) }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = with(LocalDensity.current) { maxWidth.toPx() }
        fun shift(above: Layer?): Float = if (above == null) 0f else -w * Motion.PAGE_PARALLAX * above.progress.value
        fun dimOf(above: Layer?): Float = if (above == null) 0f else Motion.PAGE_DIM * above.dim.value

        Box(Modifier.fillMaxSize().graphicsLayer { translationX = shift(layers.firstOrNull()) }) { base() }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dimOf(layers.firstOrNull()))))
        layers.forEachIndexed { i, l ->
            val above = layers.getOrNull(i + 1)
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { translationX = (1f - l.progress.value) * w + shift(above) }
                    .background(pageBg)
                    .pointerInput(Unit) { detectTapGestures { } },     // 아래 화면이 눌리지 않게
            ) {
                content(l.page)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dimOf(above))))
            }
        }
    }
}


/** 눌림 상태를 안쪽 요소(스위치 등)가 읽을 수 있게 알려 준다 */
val LocalPressed = androidx.compose.runtime.compositionLocalOf { false }

/**
 * 원본 템플릿의 눌림 효과를 그대로 옮긴 상자.
 *  - scale: 상자 전체가 줄어드는 정도 (350ms cubic-bezier(.22,.25,0,1))
 *  - innerScale: 내용만 줄어드는 정도 (목록 행: 0.992)
 *  - 눌리는 동안 위에 깔리는 반투명 물결: 라이트 검정 rippleLight / 다크 흰색 rippleDark
 *    (150ms 에 차오르고, 뗄 때 350ms 에 사라짐)
 *  - extraRippleLight/Dark: 카드 전체에 한 겹 더 깔리는 물결(메뉴 정보 행)
 */
@Composable
fun PressBox(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.ui.graphics.RectangleShape,
    scale: Float = 1f,
    innerScale: Float = 1f,
    innerFill: Boolean = false,    // 안쪽 묶음이 상자 전체를 채움 (좌표로 배치하는 내용: 줄어드는 기준점이 상자 가운데가 되도록)
    rippleLight: Float = 0.1f,
    rippleDark: Float = 0.2f,
    extraLight: Float = 0f,        // 카드 전체에 한 겹 더 깔리는 물결 (메뉴 '정보' 행: 0.06 / 다크 0.12)
    extraDark: Float = 0f,
    bg: Color = Color.Unspecified,
    border: Color = Color.Unspecified,
    contentAlignment: androidx.compose.ui.Alignment = androidx.compose.ui.Alignment.TopStart,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val c = LocalColors.current
    val hold = rememberPressHold()
    val pressed = hold.shown
    val s by animateFloatAsState(if (pressed) scale else 1f, tween(Motion.RECOIL_MS, easing = Motion.Recoil), label = "press")
    val si by animateFloatAsState(if (pressed) innerScale else 1f, tween(Motion.RECOIL_MS, easing = Motion.Recoil), label = "pressInner")
    val a = remember { Animatable(0f) }
    LaunchedEffect(pressed) {
        if (pressed) a.animateTo(1f, tween(150, easing = LinearEasing)) else a.animateTo(0f, tween(350, easing = Motion.RippleOut))
    }
    val rc = if (c.dark) Color.White.copy(alpha = rippleDark) else Color.Black.copy(alpha = rippleLight)
    val ec = if (c.dark) Color.White.copy(alpha = extraDark) else Color.Black.copy(alpha = extraLight)
    var m = modifier.graphicsLayer { scaleX = s; scaleY = s }.clip(shape)
    if (bg != Color.Unspecified) m = m.background(bg)
    m = m.drawWithContent {
        drawContent()
        if (rc.alpha > 0f && a.value > 0f) drawRect(rc.copy(alpha = rc.alpha * a.value))
        if (ec.alpha > 0f && a.value > 0f) drawRect(ec.copy(alpha = ec.alpha * a.value))
    }
    if (border != Color.Unspecified) m = m.border(1.dp, border, shape)
    m = m.pressTap(hold, true, onClick)
    Box(m, contentAlignment = contentAlignment) {
        androidx.compose.runtime.CompositionLocalProvider(LocalPressed provides pressed) {
            if (innerScale != 1f) Box((if (innerFill) Modifier.fillMaxSize() else Modifier).graphicsLayer { scaleX = si; scaleY = si }, contentAlignment = contentAlignment) { content() }
            else content()
        }
    }
}

/** 안쪽 요소가 눌려도 바깥 카드가 함께 줄어드는 효과 (홈 대시보드 카드: 0.992). 눌림은 소비하지 않는다. */
@Composable
fun Modifier.pressScaleObserver(scale: Float): Modifier {
    val hold = rememberPressHold()
    val s by animateFloatAsState(if (hold.shown) scale else 1f, tween(Motion.RECOIL_MS, easing = Motion.Recoil), label = "observe")
    return this.graphicsLayer { scaleX = s; scaleY = s }.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            hold.down = true
            waitForUpOrCancellation(PointerEventPass.Initial)
            hold.down = false
        }
    }
}

/** 배경을 기록해 둔 레이어. 반투명 + backdrop-filter: blur 인 요소(학교 선택 메뉴, 토스트)가 뒤 화면을 흐리게 비추는 데 쓴다. */
val LocalBackdrop = androidx.compose.runtime.compositionLocalOf<androidx.compose.ui.graphics.layer.GraphicsLayer?> { null }

/**
 * CSS backdrop-filter: blur(radius) 와 같은 효과: 이 요소 뒤에 그려진 화면을 가져와 흐리게 그린다.
 * (안드로이드 12 이상. 그보다 낮으면 반투명 배경만 보인다)
 */
@Composable
fun Modifier.backdropBlur(radius: androidx.compose.ui.unit.Dp): Modifier {
    val backdrop = LocalBackdrop.current ?: return this
    if (android.os.Build.VERSION.SDK_INT < 31) return this
    val layer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    return this.onGloballyPositioned { origin = it.positionInRoot() }.drawBehind {
        val r = radius.toPx()
        layer.renderEffect = androidx.compose.ui.graphics.BlurEffect(r, r, androidx.compose.ui.graphics.TileMode.Clamp)
        layer.record(this, layoutDirection, androidx.compose.ui.unit.IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))) {
            translate(-origin.x, -origin.y) { drawLayer(backdrop) }
        }
        drawLayer(layer)
    }
}
