package kr.jdms.studentid.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * 부드러운 둥근 모서리(곡률이 서서히 변하는 모서리)의 사각형 경로.
 * 모서리 곡선은 반지름 r 의 약 1.28배 길이에 걸쳐 변과 매끄럽게 이어진다.
 */
fun smoothRoundRect(w: Float, h: Float, radius: Float): Path {
    val m = minOf(w, h) / 2f
    val r = radius.coerceIn(0f, m); val t = if (m == 0f) 0f else r / m
    val k1 = if (t <= .5f) 1f else 1f - minOf(1f, (t - .5f) / .4f) * 0.138778f
    val k2 = if (t > .6f) 1f + minOf(1f, (t - .6f) / .3f) * 0.042454f else 1f
    val s = r / 100f
    val dx = minOf(w / 2, 128.19f * k1 * s); val dy = minOf(h / 2, 128.19f * k1 * s)
    val p = Path()
    if (r <= 0f) { p.addRect(androidx.compose.ui.geometry.Rect(0f, 0f, w, h)); return p }

    // 한 모서리의 곡선 3조각. 세로 변 위의 점 → 가로 변 위의 점 방향(정방향)의 절대 좌표.
    // sx,sy = 안쪽으로 향하는 부호, ox,oy = 모서리 위치
    fun corner(sx: Float, sy: Float, ox: Float, oy: Float, reversed: Boolean) {
        fun X(a: Float) = ox + sx * a * s
        fun Y(b: Float) = oy + sy * b * s
        // 각 조각: (제어점1, 제어점2, 끝점)
        val segs = listOf(
            floatArrayOf(X(0f), Y(83.62f * k2), X(4.64f), Y(67.45f), X(13.36f), Y(51.16f)),
            floatArrayOf(X(22.07f), Y(34.86f), X(34.86f), Y(22.07f), X(51.16f), Y(13.36f)),
            floatArrayOf(X(67.45f), Y(4.64f), X(83.62f * k2), Y(0f), ox + sx * dx, oy),
        )
        val start = floatArrayOf(ox, oy + sy * dy)                 // 정방향 시작점(세로 변 위)
        if (!reversed) {
            for (g in segs) p.cubicTo(g[0], g[1], g[2], g[3], g[4], g[5])
        } else {
            // 가로 변 위의 점에서 시작해 세로 변 위의 점으로 되돌아가는 방향: 점 순서를 뒤집고 제어점도 맞바꾼다
            val pts = ArrayList<FloatArray>()                      // 정방향 점들: start, seg0 끝, seg1 끝, seg2 끝
            pts.add(start); pts.add(floatArrayOf(segs[0][4], segs[0][5])); pts.add(floatArrayOf(segs[1][4], segs[1][5])); pts.add(floatArrayOf(segs[2][4], segs[2][5]))
            for (i in 2 downTo 0) {
                val g = segs[i]
                val to = pts[i]
                p.cubicTo(g[2], g[3], g[0], g[1], to[0], to[1])
            }
        }
    }

    // 시계 방향: 왼쪽 위 → 오른쪽 위 → 오른쪽 아래 → 왼쪽 아래
    p.moveTo(0f, dy)
    corner(+1f, +1f, 0f, 0f, reversed = false)            // 왼쪽 위: (0,dy) → (dx,0)
    p.lineTo(w - dx, 0f)
    corner(-1f, +1f, w, 0f, reversed = true)              // 오른쪽 위: (w-dx,0) → (w,dy)
    p.lineTo(w, h - dy)
    corner(-1f, -1f, w, h, reversed = false)              // 오른쪽 아래: (w,h-dy) → (w-dx,h)
    p.lineTo(dx, h)
    corner(+1f, -1f, 0f, h, reversed = true)              // 왼쪽 아래: (dx,h) → (0,h-dy)
    p.close()
    return p
}

class SmoothRoundedShape(private val radius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(smoothRoundRect(size.width, size.height, with(density) { radius.toPx() }))
}
