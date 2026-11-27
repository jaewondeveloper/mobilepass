package kr.jdms.studentid.school

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * 홈에 보여 줄 시간표의 날짜 규칙:
 *  - 오후 4시 5분 이후 ~ 자정 전: 다음 등교일(보통 내일) 시간표
 *  - 자정 이후 ~ 오후 4시 5분 전: 오늘 시간표
 *  - 주말이면 월요일 시간표
 */
object TimetableView {
    private val CUTOFF = LocalTime.of(16, 5)

    fun target(now: LocalDateTime): LocalDate {
        var d = if (!now.toLocalTime().isBefore(CUTOFF)) now.toLocalDate().plusDays(1) else now.toLocalDate()
        while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) d = d.plusDays(1)
        return d
    }

    fun label(target: LocalDate, today: LocalDate): String = when (target) {
        today -> "오늘 시간표"
        today.plusDays(1) -> "내일 시간표"
        else -> target.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN) + " 시간표"
    }
}
