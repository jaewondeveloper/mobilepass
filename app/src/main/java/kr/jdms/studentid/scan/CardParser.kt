package kr.jdms.studentid.scan

import java.text.Normalizer
import kotlin.math.abs

/** OCR이 읽은 한 줄(텍스트 + 위치). 좌표 단위는 상관없고 서로 같은 기준이면 된다. */
data class OcrLine(
    val text: String,
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
) {
    val centerY: Float get() = (top + bottom) / 2f
    val height: Float get() = bottom - top
    val hasGeometry: Boolean get() = right > left && bottom > top
}

data class ParsedText(
    val hasSchool: Boolean,
    val hasTitle: Boolean,
    val name: String?,
    val ocrCode: String?,
    val hasPrincipal: Boolean = false,   // '중동중학교장' (뒷면 직인 글자)
    val expiry: String? = null,          // 유효기간 끝 날짜 'yyyy-MM-dd'
    val hasAddress: Boolean = false,     // 학교 주소
    val hasTel: Boolean = false,         // 학교 전화번호
)

/**
 * 중동중학교 학생증 텍스트 해석기. (안드로이드 의존성 없음 → JVM 단위 테스트 가능)
 *
 * - 학교명/'학생증' 문구: OCR 오타를 허용하는 근사 검색
 * - 이름: '이름' 라벨 옆(같은 줄) → 라벨 오른쪽/아래 줄(위치 기반) 순으로 탐색
 * - 학번 코드(바코드 식별 코드): 영문 1자 + 숫자 7자
 */
object CardParser {
    const val SCHOOL = "중동중학교"
    private val CODE_RE = Regex("^[A-Z][0-9]{7}$")
    private val CODE_SCAN_RE = Regex("[A-Z][0-9]{7}")

    private val NAME_LABEL = "(?:이\\s*[름룸륨릅]|성\\s*명)"
    private val INLINE_NAME = Regex("$NAME_LABEL\\s*[:;.·]?\\s*([가-힣]{2,4})(?![가-힣])")
    private val INLINE_NAME_SPACED = Regex("$NAME_LABEL\\s*[:;.·]?\\s*((?:[가-힣]\\s){1,3}[가-힣])(?![가-힣])")
    private val LEADING_NAME = Regex("^[\\s:;.·]*([가-힣]{2,4})(?![가-힣])")
    private val LEADING_NAME_SPACED = Regex("^[\\s:;.·]*((?:[가-힣]\\s){1,3}[가-힣])(?![가-힣])")
    private val TITLE_FUZZY = Regex("학[생샘섕]증")

    private val NAME_STOP = setOf(
        "학생증", "중학교", "중동중학교", "모바일", "학번", "학년", "이름", "성명", "발급일", "유효기간",
        "생년월일", "학교", "교장", "학생", "학급", "번호", "소속", "바코드", "모바일학생증",
    )
    private val NAME_STOP_PARTS = listOf("학교", "학생", "학년", "학번", "학급", "발급", "유효")

    // OCR이 숫자를 문자로 혼동하는 경우 (코드의 숫자 자리에서만 사용)
    private val DIGIT_LIKE = mapOf(
        'O' to '0', 'o' to '0', 'Q' to '0', 'D' to '0',
        'I' to '1', 'l' to '1', 'i' to '1', '|' to '1',
        'Z' to '2', 'S' to '5', 's' to '5', 'G' to '6', 'B' to '8',
    )

    fun normalize(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKC)

    private fun squash(s: String): String = s.filter { !it.isWhitespace() && it != '​' }

    /** 바코드/텍스트에서 읽은 값이 학번 코드 형식이면 정규화해서 돌려주고, 아니면 null. */
    fun normalizeCode(raw: String?): String? {
        if (raw == null) return null
        val s = squash(normalize(raw)).uppercase()
        return if (CODE_RE.matches(s)) s else null
    }

    fun isValidCode(code: String?): Boolean = code != null && CODE_RE.matches(code)

    /** 문자열 안에 pattern과 편집거리 maxErrors 이하로 비슷한 부분이 있는지 (Sellers 알고리즘). */
    fun fuzzyContains(text: String, pattern: String, maxErrors: Int): Boolean {
        if (pattern.isEmpty()) return true
        if (maxErrors <= 0) return text.contains(pattern)
        val m = pattern.length
        var prev = IntArray(m + 1) { it }
        for (c in text) {
            val cur = IntArray(m + 1)
            for (i in 1..m) {
                val cost = if (pattern[i - 1] == c) 0 else 1
                cur[i] = minOf(prev[i - 1] + cost, prev[i] + 1, cur[i - 1] + 1)
            }
            if (cur[m] <= maxErrors) return true
            prev = cur
        }
        return false
    }

    fun parse(rawLines: List<OcrLine>): ParsedText {
        val lines = rawLines.map { it.copy(text = normalize(it.text)) }
        val all = lines.joinToString("") { squash(it.text) }
        // '중앙중학교'처럼 한 글자만 다른 다른 학교가 통과하지 않도록, 근사 일치는 '중동'이 있을 때만 허용
        val hasSchool = all.contains(SCHOOL) || (all.contains("중동") && fuzzyContains(all, SCHOOL, 1))
        val hasTitle = all.contains("학생증") || TITLE_FUZZY.containsMatchIn(all)
        return ParsedText(
            hasSchool, hasTitle, extractName(lines), extractOcrCode(all),
            hasPrincipal = hasPrincipal(all), expiry = extractExpiry(all),
            hasAddress = hasAddress(all), hasTel = hasTel(all),
        )
    }

    private val RANGE_RE = Regex("(20\\d\\d)[.,·](\\d{1,2})[.,·](\\d{1,2})[.,·]?[~\\-–—∼〜](20\\d\\d)[.,·](\\d{1,2})[.,·](\\d{1,2})")

    /** 뒷면 아래 '중동중학교장' (앞면의 '중동중학교'만으로는 통과하지 않도록 '장'까지 확인) */
    internal fun hasPrincipal(all: String): Boolean =
        all.contains("중동중학교장") ||
            (all.contains("중동") && Regex("(학교|학괴|학고)[장징창]").containsMatchIn(all)) ||
            (all.contains("중동") && all.contains("교장"))

    /** 서울시 강남구 일원로 8길 37 (글자 2개까지 틀려도 인정) */
    internal fun hasAddress(all: String): Boolean = fuzzyContains(all, "강남구일원로8길37", 2)

    /** TEL.070-7092-9300 → 숫자만 모아 비교 (1자리 오차 허용) */
    internal fun hasTel(all: String): Boolean = fuzzyContains(all.filter { it in '0'..'9' }, "07070929300", 1)

    /** '유효기간: 2025.3.4.~2028.2.28.' → 끝 날짜 2028-02-28. 형식이 이상하면 null */
    internal fun extractExpiry(all: String): String? {
        for (m in RANGE_RE.findAll(all)) {
            val g = m.groupValues.drop(1).map { it.toInt() }
            val y1 = g[0]; val m1 = g[1]; val d1 = g[2]; val y2 = g[3]; val m2 = g[4]; val d2 = g[5]
            if (m1 !in 1..12 || m2 !in 1..12 || d1 !in 1..31 || d2 !in 1..31) continue
            val a = y1 * 10000 + m1 * 100 + d1
            val b = y2 * 10000 + m2 * 100 + d2
            if (b <= a || y2 - y1 > 8) continue
            return "%04d-%02d-%02d".format(y2, m2, d2)
        }
        return null
    }

    /** 앞면: 사진 아래 큰 이름이 뒷면에서 읽은 이름과 같은지 (글자 사이 공백 무시) */
    fun frontNameMatches(rawLines: List<OcrLine>, name: String): Boolean {
        val n = squash(normalize(name))
        if (n.isEmpty()) return false
        return rawLines.any {
            val t = squash(normalize(it.text))
            t == n || (t.length <= n.length + 2 && t.contains(n))
        }
    }

    /** 앞면: 아래쪽 '중동중학교' */
    fun frontHasSchool(rawLines: List<OcrLine>): Boolean = parse(rawLines).hasSchool

    private fun cleanName(raw: String): String? {
        val n = squash(raw)
        if (n.length !in 2..4) return null
        if (!n.all { it in '가'..'힣' }) return null
        if (n in NAME_STOP) return null
        if (NAME_STOP_PARTS.any { n.contains(it) }) return null
        return n
    }

    private fun matchLeading(text: String): String? {
        LEADING_NAME.find(text)?.let { m -> cleanName(m.groupValues[1])?.let { return it } }
        LEADING_NAME_SPACED.find(text)?.let { m -> cleanName(m.groupValues[1])?.let { return it } }
        return null
    }

    internal fun extractName(lines: List<OcrLine>): String? {
        // 1) 같은 줄: "이름 홍길동", "이름: 홍 길 동"
        for (l in lines) {
            INLINE_NAME.find(l.text)?.let { m -> cleanName(m.groupValues[1])?.let { return it } }
            INLINE_NAME_SPACED.find(l.text)?.let { m -> cleanName(m.groupValues[1])?.let { return it } }
        }
        // 2) 라벨만 있는 줄: 위치로 오른쪽(같은 행) → 아래 줄 순서로 찾는다
        val labels = lines.filter {
            val s = squash(it.text).trimEnd(':', ';', '.', '·')
            it.hasGeometry && s.length <= 3 && (s.startsWith("이름") || s.startsWith("이룸") || s.startsWith("성명"))
        }
        for (label in labels) {
            val h = label.height
            val sameRow = lines
                .filter { it !== label && it.hasGeometry && abs(it.centerY - label.centerY) < h * 0.8f && it.left >= label.right - h * 0.3f }
                .sortedBy { it.left }
            for (c in sameRow) matchLeading(c.text)?.let { return it }
            val below = lines
                .filter { it !== label && it.hasGeometry && it.top >= label.bottom - h * 0.3f && it.top - label.bottom < h * 1.8f && it.right > label.left }
                .sortedBy { it.top }
            for (c in below) matchLeading(c.text)?.let { return it }
        }
        return null
    }

    /** 바코드를 못 읽었을 때를 대비한 보조 수단: 인쇄된 코드를 OCR로 읽는다. */
    internal fun extractOcrCode(all: String): String? {
        CODE_SCAN_RE.find(all)?.let { return it.value }
        var i = 0
        while (i + 8 <= all.length) {
            val c0 = all[i]
            if (c0 in 'A'..'Z') {
                var digits = 0
                var ok = true
                val sb = StringBuilder().append(c0)
                for (j in 1..7) {
                    val ch = all[i + j]
                    if (ch in '0'..'9') {
                        digits++
                        sb.append(ch)
                    } else {
                        val mapped = DIGIT_LIKE[ch]
                        if (mapped == null) { ok = false; break }
                        sb.append(mapped)
                    }
                }
                if (ok && digits >= 4) return sb.toString()
            }
            i++
        }
        return null
    }
}
