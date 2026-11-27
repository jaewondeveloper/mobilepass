package kr.jdms.studentid.face

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 두 단계(앞면 얼굴 → 셀피) 사이에서만 메모리에 잠시 들고 있는 값. 저장하지 않고, 끝나면 지운다. */
object FaceSession {
    @Volatile var cardEmbedding: FloatArray? = null
    @Volatile var pendingPortrait: ByteArray? = null

    fun clear() {
        cardEmbedding = null
        pendingPortrait = null
    }
}

/** ML Kit 얼굴 결과를 앱에서 쓰는 형태로. 눈/입꼬리는 영상에서 왼쪽→오른쪽 순서로 정렬해 둔다. */
data class FaceInfo(
    val box: Rect,
    val eyeL: Pt, val eyeR: Pt,
    val mouthL: Pt, val mouthR: Pt,
    val yaw: Float, val pitch: Float, val roll: Float,
    val eyesOpen: Float?,
    val landmarksReal: Boolean,
)

fun Face.toInfo(): FaceInfo {
    val b = boundingBox
    fun lm(type: Int) = getLandmark(type)?.position?.let { Pt(it.x, it.y) }
    val le = lm(FaceLandmark.LEFT_EYE)
    val re = lm(FaceLandmark.RIGHT_EYE)
    val ml = lm(FaceLandmark.MOUTH_LEFT)
    val mr = lm(FaceLandmark.MOUTH_RIGHT)
    val real = le != null && re != null && ml != null && mr != null
    val w = b.width().toFloat()
    val h = b.height().toFloat()
    // 랜드마크가 없으면(작은 얼굴 등) 박스 비율로 추정
    val eyes = FaceMath.order(le ?: Pt(b.left + 0.30f * w, b.top + 0.38f * h), re ?: Pt(b.left + 0.70f * w, b.top + 0.38f * h))
    val mouth = FaceMath.order(ml ?: Pt(b.left + 0.36f * w, b.top + 0.78f * h), mr ?: Pt(b.left + 0.64f * w, b.top + 0.78f * h))
    val lo = leftEyeOpenProbability
    val ro = rightEyeOpenProbability
    val open = if (lo != null && ro != null) min(lo, ro) else null
    return FaceInfo(b, eyes.first, eyes.second, mouth.first, mouth.second, headEulerAngleY, headEulerAngleX, headEulerAngleZ, open, real)
}

fun makeFaceDetector(fast: Boolean, minFace: Float, classification: Boolean): FaceDetector =
    FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(if (fast) FaceDetectorOptions.PERFORMANCE_MODE_FAST else FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(if (classification) FaceDetectorOptions.CLASSIFICATION_MODE_ALL else FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(minFace)
            .build(),
    )

private val FILTER = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

/**
 * 눈·입꼬리를 표준 위치에 맞춰 112x112 로 정렬한 얼굴. (기울기·크기·위치 보정)
 * 크게 줄여야 할 때는 먼저 절반 크기로 줄여 앨리어싱을 막는다.
 */
fun alignFace(src: Bitmap, f: FaceInfo): Bitmap {
    var bmp = src
    var pts = listOf(f.eyeL, f.eyeR, f.mouthL, f.mouthR)
    var t = FaceMath.similarity(pts, FaceMath.TEMPLATE_112)
    val s = FaceMath.scaleOf(t)
    var scaledCopy: Bitmap? = null
    if (s < 0.5f) {
        val pre = max(s * 2f, 0.02f)
        val sw = max(2, (src.width * pre).roundToInt())
        val sh = max(2, (src.height * pre).roundToInt())
        scaledCopy = Bitmap.createScaledBitmap(src, sw, sh, true)
        bmp = scaledCopy
        pts = pts.map { Pt(it.x * pre, it.y * pre) }
        t = FaceMath.similarity(pts, FaceMath.TEMPLATE_112)
    }
    val out = Bitmap.createBitmap(112, 112, Bitmap.Config.ARGB_8888)
    val m = Matrix().apply { setValues(floatArrayOf(t[0], -t[1], t[2], t[1], t[0], t[3], 0f, 0f, 1f)) }
    Canvas(out).drawBitmap(bmp, m, FILTER)
    scaledCopy?.recycle()
    return out
}

fun Bitmap.grayPixels(): IntArray {
    val px = IntArray(width * height)
    getPixels(px, 0, width, 0, 0, width, height)
    for (i in px.indices) {
        val p = px[i]
        px[i] = ((0.299 * ((p shr 16) and 0xFF) + 0.587 * ((p shr 8) and 0xFF) + 0.114 * (p and 0xFF))).toInt()
    }
    return px
}

/** 얼굴 영역의 선명도 (너무 큰 영역은 줄여서 계산) */
fun sharpnessOf(frame: Bitmap, box: Rect): Double {
    val l = box.left.coerceIn(0, frame.width - 2)
    val t = box.top.coerceIn(0, frame.height - 2)
    val w = box.width().coerceIn(2, frame.width - l)
    val h = box.height().coerceIn(2, frame.height - t)
    val crop = Bitmap.createBitmap(frame, l, t, w, h)
    val target = 120
    val scaled = if (w > target) Bitmap.createScaledBitmap(crop, target, max(2, h * target / w), true) else crop
    val g = scaled.grayPixels()
    val v = FaceMath.sharpness(g, scaled.width, scaled.height)
    if (scaled !== crop) scaled.recycle()
    if (crop !== frame) crop.recycle()
    return v
}

fun meanLumaOf(frame: Bitmap, box: Rect): Float {
    val l = box.left.coerceIn(0, frame.width - 2)
    val t = box.top.coerceIn(0, frame.height - 2)
    val w = box.width().coerceIn(2, frame.width - l)
    val h = box.height().coerceIn(2, frame.height - t)
    val stepX = max(1, w / 24)
    val stepY = max(1, h / 24)
    var sum = 0.0
    var n = 0
    var y = t
    while (y < t + h) {
        var x = l
        while (x < l + w) {
            val p = frame.getPixel(x, y)
            sum += 0.299 * ((p shr 16) and 0xFF) + 0.587 * ((p shr 8) and 0xFF) + 0.114 * (p and 0xFF)
            n++
            x += stepX
        }
        y += stepY
    }
    return if (n == 0) 0f else (sum / n).toFloat()
}

/**
 * 학생증 속 작은 증명사진(화질 낮음)용 보정: 인쇄 망점 제거(약한 블러) → 대비 늘리기 → 약한 샤픈.
 * 정렬된 112x112 얼굴에만 쓴다. 얼굴 '비교'용이며 화면에는 쓰지 않는다.
 */
fun enhanceCardFace(aligned: Bitmap): Bitmap {
    val w = aligned.width
    val h = aligned.height
    val px = IntArray(w * h)
    aligned.getPixels(px, 0, w, 0, 0, w, h)
    val r = IntArray(w * h) { (px[it] shr 16) and 0xFF }
    val g = IntArray(w * h) { (px[it] shr 8) and 0xFF }
    val b = IntArray(w * h) { px[it] and 0xFF }
    val chans = arrayOf(r, g, b)
    // 대비: 밝기 2~98 퍼센타일을 0~255 로 늘린다
    val hist = IntArray(256)
    for (i in px.indices) hist[((0.299 * r[i] + 0.587 * g[i] + 0.114 * b[i])).toInt().coerceIn(0, 255)]++
    fun pct(p: Double): Int { var acc = 0; val target = (p * px.size).toInt(); for (v in 0..255) { acc += hist[v]; if (acc >= target) return v }; return 255 }
    val lo = pct(0.02)
    val hi = max(pct(0.98), lo + 32)
    val out = IntArray(w * h)
    val blurred = Array(3) { c -> blur3(chans[c], w, h) }
    val blurred2 = Array(3) { c -> blur3(blurred[c], w, h) }
    for (i in out.indices) {
        val v = IntArray(3)
        for (c in 0..2) {
            val soft = blurred[c][i]
            val sharp = soft + 0.6f * (soft - blurred2[c][i])               // 약한 언샤프
            v[c] = (((sharp - lo) * 255f / (hi - lo))).roundToInt().coerceIn(0, 255)
        }
        out[i] = (0xFF shl 24) or (v[0] shl 16) or (v[1] shl 8) or v[2]
    }
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bmp.setPixels(out, 0, w, 0, 0, w, h)
    return bmp
}

/** 3x3 가우시안 근사 블러 (1 2 1) */
private fun blur3(src: IntArray, w: Int, h: Int): IntArray = blur3(FloatArray(src.size) { src[it].toFloat() }, w, h).let { f -> IntArray(f.size) { f[it].roundToInt() } }

private fun blur3(src: FloatArray, w: Int, h: Int): FloatArray {
    val tmp = FloatArray(src.size)
    val out = FloatArray(src.size)
    for (y in 0 until h) for (x in 0 until w) {
        val a = src[y * w + max(0, x - 1)]; val c = src[y * w + x]; val d = src[y * w + min(w - 1, x + 1)]
        tmp[y * w + x] = (a + 2 * c + d) / 4f
    }
    for (y in 0 until h) for (x in 0 until w) {
        val a = tmp[max(0, y - 1) * w + x]; val c = tmp[y * w + x]; val d = tmp[min(h - 1, y + 1) * w + x]
        out[y * w + x] = (a + 2 * c + d) / 4f
    }
    return out
}
