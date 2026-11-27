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
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    const val RECOIL_MS = 350
    const val PAGE_IN_MS = 450
    const val PAGE_OUT_MS = 400
    const val PAGE_PARALLAX = 1f / 3f
    const val PAGE_DIM = 0.2f          // brightness(0.8)
}

/** 누르는 동안 살짝 줄어들었다가 떼면 부드럽게 돌아오는 템플릿 버튼의 눌림 효과 + 클릭 */
@Composable
fun Modifier.recoilClickable(scale: Float = 0.96f, enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) scale else 1f, tween(Motion.RECOIL_MS, easing = Motion.Recoil), label = "recoil")
    return this.graphicsLayer { scaleX = s; scaleY = s }.clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
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
