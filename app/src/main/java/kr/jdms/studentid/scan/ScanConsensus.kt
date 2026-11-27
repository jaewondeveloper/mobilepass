package kr.jdms.studentid.scan

/** 한 장의 프레임(또는 정지 촬영)에서 읽은 결과. */
data class Observation(
    val barcode: String? = null,   // 바코드 디코더가 읽은 값(형식 검증 완료)
    val ocrCode: String? = null,   // 인쇄된 코드를 OCR로 읽은 값 (보조)
    val name: String? = null,
    val hasSchool: Boolean = false,
    val hasTitle: Boolean = false,
)

data class ScanResult(val code: String, val name: String?)

/**
 * 여러 프레임의 결과를 모아 '합의'가 된 값만 채택한다. (안드로이드 의존성 없음)
 *
 * - 점수: 바코드 값 2점, OCR로 읽은 코드 1점, 이름 1점 (정지 촬영은 가중치 ×3)
 * - 최근 [window]개 관측만 사용 → 카드를 바꾸거나 한두 프레임 오인식이 나도 영향이 작다
 * - 1등이 최소 점수를 넘고 2등의 2배 이상이어야 채택 (애매하면 계속 스캔)
 */
class ScanConsensus(private val window: Int = 8) {
    private class Entry(val obs: Observation, val weight: Int)

    private val entries = ArrayDeque<Entry>()

    /** 학교명이 한 번이라도 읽혔는지 (리셋 전까지 유지) */
    var schoolSeen = false
        private set

    fun add(obs: Observation, weight: Int = 1) {
        entries.addLast(Entry(obs, weight))
        while (entries.size > window) entries.removeFirst()
        if (obs.hasSchool) schoolSeen = true
    }

    fun reset() {
        entries.clear()
        schoolSeen = false
    }

    private fun codeScores(): Map<String, Int> {
        val m = HashMap<String, Int>()
        for (e in entries) {
            e.obs.barcode?.let { m.merge(it, 2 * e.weight, Int::plus) }
            e.obs.ocrCode?.let { m.merge(it, 1 * e.weight, Int::plus) }
        }
        return m
    }

    private fun nameScores(): Map<String, Int> {
        val m = HashMap<String, Int>()
        for (e in entries) e.obs.name?.let { m.merge(it, e.weight, Int::plus) }
        return m
    }

    private fun leader(scores: Map<String, Int>, minScore: Int): String? {
        val sorted = scores.entries.sortedByDescending { it.value }
        val top = sorted.firstOrNull() ?: return null
        if (top.value < minScore) return null
        val second = sorted.getOrNull(1)
        if (second != null && top.value < second.value * 2) return null
        return top.key
    }

    fun leadingCode(): String? = leader(codeScores(), MIN_CODE_SCORE)
    fun leadingName(): String? = leader(nameScores(), MIN_NAME_SCORE)

    /** 지금 정지 촬영(정밀 분석)으로 넘어가도 되는 상태인가 */
    fun isReady(allowMissingName: Boolean): Boolean =
        schoolSeen && leadingCode() != null && (leadingName() != null || allowMissingName)

    /** 합의된 최종 결과. 아직이면 null. */
    fun result(allowMissingName: Boolean): ScanResult? {
        if (!schoolSeen) return null
        val code = leadingCode() ?: return null
        val name = leadingName()
        if (name == null && !allowMissingName) return null
        return ScanResult(code, name)
    }

    companion object {
        const val MIN_CODE_SCORE = 4   // 바코드 2프레임 이상 (또는 OCR 코드 4프레임)
        const val MIN_NAME_SCORE = 2   // 같은 이름 2프레임 이상
        const val STILL_WEIGHT = 3     // 정지 촬영 한 장은 일반 프레임 3장만큼
    }
}
