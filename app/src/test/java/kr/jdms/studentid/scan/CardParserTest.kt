package kr.jdms.studentid.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardParserTest {
    private fun lines(vararg t: String) = t.map { OcrLine(it) }

    @Test fun codeNormalization() {
        assertEquals("S1234567", CardParser.normalizeCode(" s1234567 "))
        assertEquals("S1234567", CardParser.normalizeCode("S 1234567"))
        assertNull(CardParser.normalizeCode("S12345"))
        assertNull(CardParser.normalizeCode("12345678"))
        assertNull(CardParser.normalizeCode(null))
    }

    @Test fun schoolAndTitle() {
        val p = CardParser.parse(lines("중동중학교", "모바일 학생증"))
        assertTrue(p.hasSchool); assertTrue(p.hasTitle)
        // 공백/한 글자 오인식 허용
        assertTrue(CardParser.parse(lines("중 동 중 학 교")).hasSchool)
        assertTrue(CardParser.parse(lines("중동중학고")).hasSchool)
        assertTrue(CardParser.parse(lines("학샘증")).hasTitle)
        // 다른 학교는 통과하면 안 됨
        assertFalse(CardParser.parse(lines("중앙중학교 학생증")).hasSchool)
        assertFalse(CardParser.parse(lines("한빛고등학교")).hasSchool)
    }

    @Test fun nameInline() {
        assertEquals("홍길동", CardParser.parse(lines("이름 홍길동")).name)
        assertEquals("홍길동", CardParser.parse(lines("이름: 홍길동")).name)
        assertEquals("홍길동", CardParser.parse(lines("이 름 : 홍 길 동")).name)
        assertEquals("남궁민수", CardParser.parse(lines("이름 남궁민수 학번 3")).name)
        assertEquals("김민준", CardParser.parse(lines("성명 김민준")).name)
        assertEquals("이서", CardParser.parse(lines("이름 이서")).name)
    }

    @Test fun nameRejectsNonNames() {
        assertNull(CardParser.parse(lines("이름 학생증")).name)
        assertNull(CardParser.parse(lines("이름 중동중학교")).name)
        assertNull(CardParser.parse(lines("중동중학교 학생증")).name)
    }

    @Test fun nameByGeometry() {
        val label = OcrLine("이름", 10f, 100f, 60f, 130f)
        val value = OcrLine("박지민", 80f, 102f, 160f, 132f)
        val other = OcrLine("3학년 2반", 10f, 200f, 120f, 230f)
        assertEquals("박지민", CardParser.extractName(listOf(label, other, value)))
        // 값이 라벨 바로 아래 줄에 있는 레이아웃
        val below = OcrLine("최유진", 10f, 140f, 90f, 170f)
        assertEquals("최유진", CardParser.extractName(listOf(label, other, below)))
    }

    @Test fun ocrCode() {
        assertEquals("S1234567", CardParser.parse(lines("학번 S1234567")).ocrCode)
        assertEquals("S1234567", CardParser.extractOcrCode("BS1234567"))
        // 숫자 자리의 문자 오인식 보정 (O→0, I→1)
        assertEquals("S1203456", CardParser.extractOcrCode("S12O3456"))
        assertEquals("S1023456", CardParser.extractOcrCode("SIO23456"))
        assertNull(CardParser.extractOcrCode("중동중학교학생증"))
        assertNull(CardParser.extractOcrCode("SCHOOLID"))
    }

    @Test fun fullCard() {
        val p = CardParser.parse(lines("중동중학교", "모바일 학생증", "이름 정하늘", "S7654321"))
        assertTrue(p.hasSchool)
        assertEquals("정하늘", p.name)
        assertEquals("S7654321", p.ocrCode)
    }
}
