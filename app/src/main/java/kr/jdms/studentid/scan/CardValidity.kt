package kr.jdms.studentid.scan

import java.time.LocalDate

/** 학생증 유효기간(끝 날짜 'yyyy-MM-dd', 그날까지 유효). 안드로이드 의존성 없음. */
object CardValidity {
    fun isExpired(expiry: String?, today: LocalDate = LocalDate.now()): Boolean {
        if (expiry.isNullOrEmpty()) return false      // 유효기간 없이 저장된 예전 등록분은 지우지 않는다
        val d = runCatching { LocalDate.parse(expiry) }.getOrNull() ?: return false
        return today.isAfter(d)
    }

    fun pretty(expiry: String?): String {
        val d = runCatching { LocalDate.parse(expiry) }.getOrNull() ?: return expiry ?: ""
        return "${d.year}.${d.monthValue}.${d.dayOfMonth}."
    }
}
