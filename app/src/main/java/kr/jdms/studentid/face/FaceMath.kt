package kr.jdms.studentid.face

import kotlin.math.atan2
import kotlin.math.sqrt

data class Pt(val x: Float, val y: Float)

/** 얼굴 처리에 쓰는 순수 계산 (안드로이드 의존성 없음 → JVM 단위 테스트 가능) */
object FaceMath {
    /**
     * 112x112 얼굴 정렬 기준점 (ArcFace/SFace 표준). 영상 기준 왼쪽 눈, 오른쪽 눈, 왼쪽 입꼬리, 오른쪽 입꼬리.
     * (코끝은 ML Kit 의 NOSE_BASE 와 위치 정의가 달라 쓰지 않는다)
     */
    val TEMPLATE_112 = listOf(
        Pt(38.2946f, 51.6963f), Pt(73.5318f, 51.5014f),
        Pt(41.5493f, 92.3655f), Pt(70.7299f, 92.2041f),
    )

    /** 점들을 최소제곱으로 dst 에 맞추는 닮음변환(회전+균등확대+이동). 반환: [a, b, tx, ty] → x' = a·x − b·y + tx, y' = b·x + a·y + ty */
    fun similarity(src: List<Pt>, dst: List<Pt>): FloatArray {
        require(src.size == dst.size && src.size >= 2)
        val n = src.size
        val sx = src.sumOf { it.x.toDouble() } / n
        val sy = src.sumOf { it.y.toDouble() } / n
        val dx = dst.sumOf { it.x.toDouble() } / n
        val dy = dst.sumOf { it.y.toDouble() } / n
        var num1 = 0.0
        var num2 = 0.0
        var den = 0.0
        for (i in 0 until n) {
            val px = src[i].x - sx
            val py = src[i].y - sy
            val qx = dst[i].x - dx
            val qy = dst[i].y - dy
            num1 += px * qx + py * qy
            num2 += px * qy - py * qx
            den += px * px + py * py
        }
        val a = num1 / den
        val b = num2 / den
        val tx = dx - (a * sx - b * sy)
        val ty = dy - (b * sx + a * sy)
        return floatArrayOf(a.toFloat(), b.toFloat(), tx.toFloat(), ty.toFloat())
    }

    fun apply(t: FloatArray, p: Pt) = Pt(t[0] * p.x - t[1] * p.y + t[2], t[1] * p.x + t[0] * p.y + t[3])

    /** 변환의 확대 배율 */
    fun scaleOf(t: FloatArray): Float = sqrt(t[0] * t[0] + t[1] * t[1])

    fun order(a: Pt, b: Pt): Pair<Pt, Pt> = if (a.x <= b.x) a to b else b to a

    /** 영상에서 눈을 이은 선의 기울기(도). 0 이면 수평 */
    fun rollDegrees(eyeLeftInImage: Pt, eyeRightInImage: Pt): Float =
        Math.toDegrees(atan2((eyeRightInImage.y - eyeLeftInImage.y).toDouble(), (eyeRightInImage.x - eyeLeftInImage.x).toDouble())).toFloat()

    fun normalize(v: FloatArray): FloatArray {
        var s = 0.0
        for (x in v) s += x * x
        val n = sqrt(s).toFloat()
        return if (n < 1e-9f) v.copyOf() else FloatArray(v.size) { v[it] / n }
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size)
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val d = sqrt(na) * sqrt(nb)
        return if (d < 1e-12) 0f else (dot / d).toFloat()
    }

    /** 여러 임베딩(각각 정규화) 의 평균을 다시 정규화 → 한두 장의 흔들린 프레임에 덜 민감 */
    fun meanEmbedding(list: List<FloatArray>): FloatArray {
        require(list.isNotEmpty())
        val out = FloatArray(list[0].size)
        for (e in list) {
            val n = normalize(e)
            for (i in out.indices) out[i] += n[i]
        }
        return normalize(out)
    }

    /** 라플라시안 분산: 클수록 선명 (흐린 프레임 걸러내기). gray 는 0..255 */
    fun sharpness(gray: IntArray, w: Int, h: Int): Double {
        if (w < 3 || h < 3) return 0.0
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val lap = 4 * gray[i] - gray[i - 1] - gray[i + 1] - gray[i - w] - gray[i + w]
                sum += lap
                sumSq += lap.toDouble() * lap
                n++
            }
        }
        val mean = sum / n
        return sumSq / n - mean * mean
    }
}

/** 깜빡임(눈 뜸 → 감음 → 뜸) 감지. 눈 뜬 확률(0..1)을 프레임마다 넣는다. */
class BlinkDetector(private val closedBelow: Float = 0.30f, private val openAbove: Float = 0.65f) {
    private var sawOpen = false
    private var sawClosed = false

    var blinked = false
        private set

    fun feed(eyesOpen: Float) {
        if (blinked) return
        if (eyesOpen >= openAbove) {
            if (sawClosed && sawOpen) blinked = true else sawOpen = true
        } else if (eyesOpen <= closedBelow && sawOpen) {
            sawClosed = true
        }
    }

    fun reset() {
        sawOpen = false
        sawClosed = false
        blinked = false
    }
}

/** 본인 확인 판정 기준 */
object MatchPolicy {
    /** SFace(OpenCV 권장 0.363) 보다 약간 낮춤: 학생증 사진은 화질이 낮아 같은 사람이어도 점수가 낮게 나온다. 실제 데이터로 조정 필요. */
    const val THRESHOLD = 0.363f      // OpenCV SFace 가 권장하는 코사인 유사도 기준

    fun isMatch(score: Float) = score >= THRESHOLD
}
