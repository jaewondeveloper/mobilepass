package kr.jdms.studentid.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** CSS 의 blur 반지름(B) 은 가우시안 σ = B/2. 안드로이드 setShadowLayer/BlurMaskFilter 의 radius 로 σ 가 같아지게 환산 */
private fun androidBlurRadius(cssBlurPx: Float): Float = if (cssBlurPx <= 0f) 0f else ((cssBlurPx / 2f - 0.5f) / 0.57735f).coerceAtLeast(0.5f)

/**
 * CSS `box-shadow: dx dy blur 0 color` 와 같은 바깥 그림자.
 * 안쪽(요소 자신)은 가려서 그리지 않으므로 반투명 배경(유리 카드, 탭바) 아래로 그림자가 비치지 않는다 (CSS 와 동일).
 */
fun Modifier.cssShadow(radius: Dp, blur: Dp, dy: Dp, color: Color, dx: Dp = 0.dp): Modifier = this.drawBehind {
    val r = radius.toPx()
    // 요소 자신을 가리는 구멍은 1px 더 크게: 안티앨리어싱 때문에 그림자용 검정 본체가 가장자리에 얇은 선으로 비치는 것을 막는다
    val e = 1.dp.toPx()
    val shape = Path().apply { addRoundRect(RoundRect(-e, -e, size.width + e, size.height + e, CornerRadius(r + e))) }
    clipPath(shape, ClipOp.Difference) {
        drawIntoCanvas { canvas ->
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            p.color = android.graphics.Color.BLACK
            p.setShadowLayer(androidBlurRadius(blur.toPx()), dx.toPx(), dy.toPx(), color.toArgb())
            canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, p)
        }
    }
}

/** CSS `box-shadow: 0 0 blur spread color inset` : 가장자리에서 안쪽으로 번지는 빛 (홈 유리 카드의 흰 번짐) */
fun Modifier.cssInsetGlow(radius: Dp, blur: Dp, spread: Dp, color: Color): Modifier = this.drawBehind {
    val r = radius.toPx()
    val s = spread.toPx()
    val b = blur.toPx()
    val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
    val outer = Path().apply { addRect(Rect(-b * 3, -b * 3, size.width + b * 3, size.height + b * 3)) }
    val inner = Path().apply { addRoundRect(RoundRect(s, s, size.width - s, size.height - s, CornerRadius((r - s).coerceAtLeast(0f)))) }
    val ring = Path.combine(PathOperation.Difference, outer, inner)
    clipPath(clip) {
        drawIntoCanvas { canvas ->
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            p.color = color.toArgb()
            p.maskFilter = android.graphics.BlurMaskFilter(androidBlurRadius(b), android.graphics.BlurMaskFilter.Blur.NORMAL)
            canvas.nativeCanvas.drawPath(ring.asAndroidPath(), p)
        }
    }
}
