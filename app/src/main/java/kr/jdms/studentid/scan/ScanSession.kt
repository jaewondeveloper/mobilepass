package kr.jdms.studentid.scan

/** 스캔 한 번 동안 뒷면에서 읽은 값을 앞면 확인/등록 단계로 넘기는 임시 보관소 (메모리 전용). */
object ScanSession {
    @Volatile var name: String? = null
    @Volatile var expiry: String? = null

    fun clear() { name = null; expiry = null }
}
