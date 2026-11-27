package kr.jdms.studentid.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class FaceMathTest {
    @Test fun similarityRecoversKnownTransform() {
        // 기준점을 30° 돌리고 2배 키우고 (100, 50) 이동한 점들 → 역변환이 기준점으로 되돌려야 한다
        val ang = Math.toRadians(30.0)
        val s = 2.0
        val src = FaceMath.TEMPLATE_112.map {
            Pt((s * (cos(ang) * it.x - sin(ang) * it.y) + 100).toFloat(), (s * (sin(ang) * it.x + cos(ang) * it.y) + 50).toFloat())
        }
        val t = FaceMath.similarity(src, FaceMath.TEMPLATE_112)
        assertEquals(0.5f, FaceMath.scaleOf(t), 1e-3f)
        for (i in src.indices) {
            val p = FaceMath.apply(t, src[i])
            assertEquals(FaceMath.TEMPLATE_112[i].x, p.x, 0.05f)
            assertEquals(FaceMath.TEMPLATE_112[i].y, p.y, 0.05f)
        }
    }

    @Test fun similarityToleratesNoise() {
        val noisy = FaceMath.TEMPLATE_112.mapIndexed { i, p -> Pt(p.x * 3 + 10 + (if (i % 2 == 0) 1f else -1f), p.y * 3 + 20) }
        val t = FaceMath.similarity(noisy, FaceMath.TEMPLATE_112)
        assertEquals(1f / 3f, FaceMath.scaleOf(t), 0.02f)
    }

    @Test fun rollIsZeroWhenLevelAndSignedWhenTilted() {
        assertEquals(0f, FaceMath.rollDegrees(Pt(10f, 50f), Pt(90f, 50f)), 1e-4f)
        assertEquals(45f, FaceMath.rollDegrees(Pt(0f, 0f), Pt(10f, 10f)), 1e-3f)
    }

    @Test fun cosineAndMean() {
        assertEquals(1f, FaceMath.cosine(floatArrayOf(1f, 2f, 3f), floatArrayOf(2f, 4f, 6f)), 1e-6f)
        assertEquals(0f, FaceMath.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 1e-6f)
        val m = FaceMath.meanEmbedding(listOf(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)))
        assertEquals(0.7071f, m[0], 1e-3f)
        assertEquals(1f, FaceMath.cosine(m, m), 1e-6f)
    }

    @Test fun sharpnessRanksSharpAboveBlurry() {
        val w = 32; val h = 32
        val sharp = IntArray(w * h) { if ((it % w + it / w) % 2 == 0) 255 else 0 }     // 체커보드
        val flat = IntArray(w * h) { 128 }
        assertTrue(FaceMath.sharpness(sharp, w, h) > FaceMath.sharpness(flat, w, h) + 1000)
    }

    @Test fun blinkNeedsOpenClosedOpen() {
        val b = BlinkDetector()
        listOf(0.9f, 0.95f, 0.9f).forEach { b.feed(it) }
        assertFalse(b.blinked)                       // 계속 뜨고 있으면 아님
        b.feed(0.1f)
        assertFalse(b.blinked)                       // 감기만 해서는 아님
        b.feed(0.9f)
        assertTrue(b.blinked)
    }

    @Test fun blinkIgnoresStartingWithClosedEyes() {
        val b = BlinkDetector()
        b.feed(0.1f); b.feed(0.1f)
        assertFalse(b.blinked)
        b.feed(0.9f)                                 // 처음부터 감고 있다 뜬 것은 '깜빡임'이 아님
        assertFalse(b.blinked)
    }

    @Test fun policy() {
        assertTrue(MatchPolicy.isMatch(0.5f))
        assertFalse(MatchPolicy.isMatch(0.1f))
    }
}
