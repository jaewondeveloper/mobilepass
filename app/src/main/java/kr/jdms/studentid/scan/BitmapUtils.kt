package kr.jdms.studentid.scan

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * CameraX 프레임 → 화면에 보이는 것과 똑같은 방향/영역의 비트맵.
 * (ViewPort를 공유하므로 cropRect = 미리보기에 보이는 영역, rotationDegrees = 세로 보정)
 */
fun ImageProxy.toUprightBitmap(): Bitmap {
    val src = toBitmap()
    val r = cropRect
    val x = r.left.coerceIn(0, src.width - 1)
    val y = r.top.coerceIn(0, src.height - 1)
    val w = r.width().coerceIn(1, src.width - x)
    val h = r.height().coerceIn(1, src.height - y)
    val m = Matrix().apply { postRotate(imageInfo.rotationDegrees.toFloat()) }
    val out = Bitmap.createBitmap(src, x, y, w, h, m, true)
    if (out !== src) src.recycle()
    return out
}

/**
 * 비율(0~1) 영역을 잘라낸다. [margin]은 영역 크기 대비 여유분.
 * 원본과 다른 비트맵을 만들면 원본은 recycle 한다.
 */
fun Bitmap.cropFraction(frac: RectF, margin: Float, maxSide: Int = Int.MAX_VALUE): Bitmap {
    val mw = frac.width() * margin
    val mh = frac.height() * margin
    val l = max(0f, frac.left - mw)
    val t = max(0f, frac.top - mh)
    val r = min(1f, frac.right + mw)
    val b = min(1f, frac.bottom + mh)
    val x = (l * width).roundToInt().coerceIn(0, width - 1)
    val y = (t * height).roundToInt().coerceIn(0, height - 1)
    val w = ((r - l) * width).roundToInt().coerceIn(1, width - x)
    val h = ((b - t) * height).roundToInt().coerceIn(1, height - y)
    var out = Bitmap.createBitmap(this, x, y, w, h)
    val longest = max(out.width, out.height)
    if (longest > maxSide) {
        val s = maxSide.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(out, (out.width * s).roundToInt(), (out.height * s).roundToInt(), true)
        if (scaled !== out) {
            if (out !== this) out.recycle()
            out = scaled
        }
    }
    if (out !== this) recycle()
    return out
}

/** 밝기 평균(0~255). 너무 어둡거나 빛이 반사되는지 판단하는 데 쓴다. */
fun Bitmap.meanLuma(samples: Int = 48): Float {
    val stepX = max(1, width / samples)
    val stepY = max(1, height / samples)
    var sum = 0.0
    var n = 0
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val p = getPixel(x, y)
            sum += 0.299 * ((p shr 16) and 0xFF) + 0.587 * ((p shr 8) and 0xFF) + 0.114 * (p and 0xFF)
            n++
            x += stepX
        }
        y += stepY
    }
    return if (n == 0) 0f else (sum / n).toFloat()
}
