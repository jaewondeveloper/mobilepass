package kr.jdms.studentid.scan

/** 한 장의 프레임에서 읽은 결과. */
data class Observation(
    val barcode: String? = null,   // 바코드 디코더가 읽은 값(형식 검증 완료)
    val ocrCode: String? = null,   // 인쇄된 코드를 OCR로 읽은 값 (보조)
    val name: String? = null,
    val hasSchool: Boolean = false,
    val hasTitle: Boolean = false,
)

data class ScanResult(val code: String, val name: String)

/**
 * 최근 프레임들의 결과를 모아 값을 확정한다. (안드로이드 의존성 없음)
 *
 * - 학교명 + 바코드 + 이름이 **한 프레임에 모두** 읽히면 바로 확정 (빠른 경로, 보통 첫 몇 프레임)
 * - 그렇지 않아도 여러 프레임에 걸쳐 모이면 확정
 * - 서로 다른 값이 비슷하게 섞이면(1등이 2등의 2배 미만) 보류하고 계속 스캔 → 오인식 방지
 * - 바코드는 형식 검증(영문 1 + 숫자 7)을 통과한 값만 쓰고, 최근 [window]개 프레임만 반영
 * - 이름이 없으면 확정하지 않는다 (직접 입력 같은 임의 UI를 두지 않음)
 */
class ScanConsensus(private val window: Int = 6) {
    private val entries = ArrayDeque<Observation>()

    /** 학교명이 한 번이라도 읽혔는지 (리셋 전까지 유지) */
    var schoolSeen = false
        private set

    fun add(obs: Observation) {
        entries.addLast(obs)
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
            e.barcode?.let { m.merge(it, 2, Int::plus) }
            e.ocrCode?.let { m.merge(it, 1, Int::plus) }
        }
        return m
    }

    private fun nameScores(): Map<String, Int> {
        val m = HashMap<String, Int>()
        for (e in entries) e.name?.let { m.merge(it, 1, Int::plus) }
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

    /** 합의된 최종 결과. 아직이면 null. */
    fun result(): ScanResult? {
        if (!schoolSeen) return null
        val code = leadingCode() ?: return null
        val name = leadingName() ?: return null
        return ScanResult(code, name)
    }

    companion object {
        const val MIN_CODE_SCORE = 2   // 바코드 1프레임 (OCR 코드만이면 2프레임)
        const val MIN_NAME_SCORE = 1
    }
}
