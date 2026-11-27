package kr.jdms.studentid.school

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.util.Base64

/**
 * 컴시간알리미(컴시간학생.kr)의 학생용 시간표 서버에서 시간표를 가져온다.
 * 공식 API 가 없는 서비스라 학생용 웹페이지가 쓰는 요청 방식을 그대로 따라 하며,
 * 사이트가 바뀌어 읽지 못하면 예외를 던진다 → 호출하는 쪽이 나이스 시간표로 대신 보여 준다.
 *
 *  1) /st 페이지에서 학교 검색 주소 조각(school_ra)과 데이터 접두어(sc_data)를 읽는다.
 *  2) 학교 이름(EUC-KR 16진 표기)으로 검색해 학교 번호를 얻는다.
 *  3) base64("접두어 + 학교번호 + _0_1") 로 이번 주 시간표 데이터를 받아 학년/반/요일/교시 표로 푼다.
 */
class Comcigan(private val f: Fetcher, private val host: String = "http://comci.net:4082") {
    class Site(val path: String, val query: String, val prefix: String)

    fun readSite(page: String): Site {
        val i = page.indexOf("school_ra(sc)")
        val j = page.indexOf("sc_data(")
        if (i < 0 || j < 0) throw IllegalStateException("컴시간 주소 정보를 찾지 못함")
        val ra = Regex("url:'\\.([^']*)'").find(page.substring(i, minOf(page.length, i + 200)))?.groupValues?.get(1)
            ?: throw IllegalStateException("컴시간 검색 주소를 찾지 못함")
        val prefix = Regex("sc_data\\('([^']*)'").find(page.substring(j, minOf(page.length, j + 80)))?.groupValues?.get(1)
            ?: throw IllegalStateException("컴시간 접두어를 찾지 못함")
        val path = ra.substringBefore('?')
        val query = ra.substringAfter('?', "")
        return Site(path, query, prefix)
    }

    /** 응답 뒤에 붙는 군더더기를 버리고 JSON 으로 읽는다 */
    fun json(body: String): JSONObject = JSONObject(body.substring(0, body.lastIndexOf('}') + 1))

    /** 검색 결과: [지역코드, 지역명, 학교명, 학교번호] 목록 → (지역명, 학교번호) */
    fun parseSearch(body: String, schoolName: String): List<Pair<String, Int>> {
        val arr: JSONArray = json(body).optJSONArray("학교검색") ?: return emptyList()
        val out = ArrayList<Pair<String, Int>>()
        for (i in 0 until arr.length()) {
            val a = arr.getJSONArray(i)
            if (a.length() >= 4 && a.getString(2) == schoolName) out.add(a.getString(1) to a.getInt(3))
        }
        return out
    }

    private fun names(o: JSONObject, key: String): List<String> {
        val a = o.optJSONArray(key) ?: return emptyList()
        return List(a.length()) { a.optString(it) }
    }

    /** 시간표 데이터(JSON) → 한 반의 이번 주 시간표 */
    fun parseTimetable(data: JSONObject, grade: Int, cls: Int, days: List<LocalDate>): Timetable {
        val table = data.getJSONArray("자료147").optJSONArray(grade)?.optJSONArray(cls)
            ?: throw IllegalStateException("해당 학년/반 시간표가 없음")
        val subjects = names(data, "자료492")
        val teachers = names(data, "자료481")
        val sep = data.optInt("분리", 100).let { if (it <= 0) 100 else it }
        val outDays = ArrayList<DayTimetable>()
        for ((idx, date) in days.withIndex()) {
            val dayArr = table.optJSONArray(idx + 1) ?: continue
            val lessons = ArrayList<Lesson>()
            for (p in 1 until dayArr.length()) {
                val th = dayArr.optInt(p, 0)
                if (th <= 0) continue
                var subj = th / sep
                var teach = th % sep
                // 번호가 표 범위를 벗어나면 과목/교사 자리를 바꿔서 읽어 본다
                if (subj >= subjects.size && teach < subjects.size && subj < teachers.size) { val t = subj; subj = teach; teach = t }
                val name = subjects.getOrNull(subj).orEmpty()
                if (name.isEmpty()) continue
                lessons.add(Lesson(p, name, teachers.getOrNull(teach).orEmpty()))
            }
            outDays.add(DayTimetable(Neis.ymd(date), lessons))
        }
        return Timetable("컴시간", grade, cls, outDays)
    }

    /** schoolName 학교의 (grade)학년 (cls)반 시간표. regionHint 는 지역이 겹치는 학교를 고를 때 쓰는 단서(예: 나이스 주소). */
    fun timetable(schoolName: String, regionHint: String, grade: Int, cls: Int, days: List<LocalDate>): Timetable {
        val site = readSite(f.get("$host/st", "EUC-KR"))
        val hex = URLEncoder.encode(schoolName, "EUC-KR")
        val found = parseSearch(f.get("$host${site.path}?${site.query}$hex"), schoolName)
        if (found.isEmpty()) throw IllegalStateException("컴시간에서 학교를 찾지 못함")
        val code = (found.firstOrNull { regionHint.contains(it.first) } ?: found.first()).second
        val sc = Base64.getEncoder().encodeToString("${site.prefix}${code}_0_1".toByteArray())
        val data = json(f.get("$host${site.path}?$sc"))
        return parseTimetable(data, grade, cls, days)
    }
}
