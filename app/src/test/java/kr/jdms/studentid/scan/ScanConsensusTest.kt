package kr.jdms.studentid.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanConsensusTest {
    private fun obs(code: String? = null, name: String? = null, school: Boolean = false, ocr: String? = null) =
        Observation(barcode = code, ocrCode = ocr, name = name, hasSchool = school)

    @Test fun needsAgreementAcrossFrames() {
        val c = ScanConsensus()
        c.add(obs("S1234567", "홍길동", true))
        assertFalse(c.isReady(false))              // 한 프레임으로는 부족
        c.add(obs("S1234567", "홍길동", true))
        assertTrue(c.isReady(false))
        assertEquals(ScanResult("S1234567", "홍길동"), c.result(false))
    }

    @Test fun oneBadFrameDoesNotFlipResult() {
        val c = ScanConsensus()
        repeat(3) { c.add(obs("S1234567", "홍길동", true)) }
        c.add(obs("S7654321", "홍길똥", true))
        assertEquals(ScanResult("S1234567", "홍길동"), c.result(false))
    }

    @Test fun ambiguousIsNotAccepted() {
        val c = ScanConsensus()
        repeat(2) { c.add(obs("S1111111", "김가나", true)) }
        repeat(2) { c.add(obs("S2222222", "김다라", true)) }
        assertNull(c.result(false))
    }

    @Test fun requiresSchoolText() {
        val c = ScanConsensus()
        repeat(3) { c.add(obs("S1234567", "홍길동", false)) }
        assertNull(c.result(false))
        c.add(obs(null, null, true))
        assertEquals("S1234567", c.result(false)?.code)
    }

    @Test fun missingNameOnlyWhenAllowed() {
        val c = ScanConsensus()
        repeat(3) { c.add(obs("S1234567", null, true)) }
        assertNull(c.result(false))
        assertEquals(ScanResult("S1234567", null), c.result(true))
    }

    @Test fun ocrCodeAloneNeedsMoreFrames() {
        val c = ScanConsensus()
        repeat(3) { c.add(obs(null, "홍길동", true, ocr = "S1234567")) }
        assertNull(c.leadingCode())               // OCR 코드는 1점씩 → 3점으로는 부족
        c.add(obs(null, "홍길동", true, ocr = "S1234567"))
        assertEquals("S1234567", c.leadingCode())
    }

    @Test fun stillCanConfirmAndConflictBlocks() {
        val c = ScanConsensus()
        repeat(2) { c.add(obs("S1234567", "홍길동", true)) }
        c.add(obs("S1234567", "홍길동", true), ScanConsensus.STILL_WEIGHT)
        assertEquals("S1234567", c.result(false)?.code)

        val d = ScanConsensus()
        repeat(2) { d.add(obs("S1234567", "홍길동", true)) }
        d.add(obs("S9999999", "홍길동", true), ScanConsensus.STILL_WEIGHT)   // 정지 사진이 다른 코드를 읽음 → 보류
        assertNull(d.result(false))
    }

    @Test fun windowForgetsOldCard() {
        val c = ScanConsensus(window = 4)
        repeat(4) { c.add(obs("S1111111", "김가나", true)) }
        repeat(4) { c.add(obs("S2222222", "이다라", true)) }
        assertEquals(ScanResult("S2222222", "이다라"), c.result(false))
    }
}
