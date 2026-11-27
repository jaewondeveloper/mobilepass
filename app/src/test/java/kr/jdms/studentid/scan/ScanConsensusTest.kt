package kr.jdms.studentid.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanConsensusTest {
    private fun obs(code: String? = null, name: String? = null, school: Boolean = false, ocr: String? = null) =
        Observation(barcode = code, ocrCode = ocr, name = name, hasSchool = school)

    @Test fun singleCompleteFrameIsEnough() {
        val c = ScanConsensus()
        c.add(obs("S1234567", "홍길동", true))
        assertEquals(ScanResult("S1234567", "홍길동"), c.result())
    }

    @Test fun resultsCombineAcrossFrames() {
        val c = ScanConsensus()
        c.add(obs("S1234567", null, true))      // 이름만 못 읽은 프레임
        assertNull(c.result())
        c.add(obs(null, "홍길동", false))        // 이름만 읽은 프레임
        assertEquals(ScanResult("S1234567", "홍길동"), c.result())
    }

    @Test fun nameIsRequired() {
        val c = ScanConsensus()
        repeat(5) { c.add(obs("S1234567", null, true)) }
        assertNull(c.result())
    }

    @Test fun schoolTextIsRequired() {
        val c = ScanConsensus()
        repeat(3) { c.add(obs("S1234567", "홍길동", false)) }
        assertNull(c.result())
        c.add(obs(null, null, true))
        assertEquals("S1234567", c.result()?.code)
    }

    @Test fun majorityWinsOverOneBadFrame() {
        val c = ScanConsensus()
        repeat(3) { c.add(obs("S1234567", "홍길동", true)) }
        c.add(obs("S1234567", "홍길똥", true))
        assertEquals(ScanResult("S1234567", "홍길동"), c.result())
    }

    @Test fun ambiguousIsNotAccepted() {
        val c = ScanConsensus()
        c.add(obs("S1111111", "김가나", true))
        c.add(obs("S2222222", "김다라", true))
        assertNull(c.result())                  // 값이 갈리면 보류하고 계속 스캔
    }

    @Test fun ocrCodeAloneNeedsTwoFrames() {
        val c = ScanConsensus()
        c.add(obs(null, "홍길동", true, ocr = "S1234567"))
        assertNull(c.leadingCode())             // OCR 코드는 1점
        c.add(obs(null, "홍길동", true, ocr = "S1234567"))
        assertEquals("S1234567", c.leadingCode())
    }

    @Test fun windowForgetsOldCard() {
        val c = ScanConsensus(window = 3)
        repeat(3) { c.add(obs("S1111111", "김가나", true)) }
        repeat(3) { c.add(obs("S2222222", "이다라", true)) }
        assertEquals(ScanResult("S2222222", "이다라"), c.result())
    }
}
