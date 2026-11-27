package kr.jdms.studentid.school

import org.json.JSONObject
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 나이스 교육정보 개방 포털(open.neis.go.kr) 공개 API 를 인증키 없이 사용한다.
 * 키가 없으면 한 번에 받는 건수가 적게(약 5건) 제한되므로, 모자라면 날짜(와 교시)별로 나눠 요청한다.
 */
object Neis {
    private const val BASE = "https://open.neis.go.kr/hub"
    /** 인증키 없이는 한 번에 5건까지만 받을 수 있다 (더 크게 요청하면 거절될 수 있음) */
    private const val PAGE_SIZE = 5
    private val YMD = DateTimeFormatter.BASIC_ISO_DATE

    fun ymd(d: LocalDate): String = d.format(YMD)

    /** 이번 주 월~금 (토·일이면 다음 주) */
    fun schoolWeek(today: LocalDate): List<LocalDate> {
        val monday = when (today.dayOfWeek) {
            DayOfWeek.SATURDAY -> today.plusDays(2)
            DayOfWeek.SUNDAY -> today.plusDays(1)
            else -> today.minusDays((today.dayOfWeek.value - 1).toLong())
        }
        return (0..4).map { monday.plusDays(it.toLong()) }
    }

    fun url(endpoint: String, params: Map<String, String>): String =
        "$BASE/$endpoint?Type=json&" + params.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }

    class Page(val total: Int, val rows: List<JSONObject>, val code: String)

    /** 나이스 응답 JSON → 건수/행/결과 코드. 데이터가 없으면 최상위에 RESULT 만 온다. */
    fun parse(endpoint: String, body: String): Page {
        val root = JSONObject(body)
        root.optJSONObject("RESULT")?.let { return Page(0, emptyList(), it.optString("CODE")) }
        val arr = root.optJSONArray(endpoint) ?: return Page(0, emptyList(), "NONE")
        var total = 0
        var code = "INFO-000"
        val rows = ArrayList<JSONObject>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            o.optJSONArray("head")?.let { h ->
                for (j in 0 until h.length()) {
                    val e = h.getJSONObject(j)
                    if (e.has("list_total_count")) total = e.getInt("list_total_count")
                    e.optJSONObject("RESULT")?.let { code = it.optString("CODE", code) }
                }
            }
            o.optJSONArray("row")?.let { r -> for (j in 0 until r.length()) rows.add(r.getJSONObject(j)) }
        }
        return Page(total, rows, code)
    }

    class Fetched(val rows: List<JSONObject>, val complete: Boolean, val note: String = "")

    /** 여러 쪽에 걸친 결과를 모두 모은다. 중간에 막히면 지금까지 받은 것과 complete=false 를 돌려준다. */
    fun fetchAll(f: Fetcher, endpoint: String, params: Map<String, String>): Fetched {
        val all = ArrayList<JSONObject>()
        var index = 1
        while (index <= 60) {
            val page = try {
                parse(endpoint, f.get(url(endpoint, params + mapOf("pIndex" to "$index", "pSize" to "$PAGE_SIZE"))))
            } catch (e: Exception) {
                return Fetched(all, false, "연결 실패(${e.message ?: e.javaClass.simpleName})")
            }
            if (page.code == "INFO-200") return Fetched(all, true)                // 해당하는 데이터가 없음 = 정상
            if (page.code != "INFO-000") return Fetched(all, false, "나이스 응답 ${page.code}")               // 제한/오류
            all.addAll(page.rows)
            if (page.rows.isEmpty() || all.size >= page.total) return Fetched(all, true)
            index++
        }
        return Fetched(all, false)
    }

    // ───────── 학교 찾기 ─────────

    /** 이름이 같은 학교가 여러 곳(서울 강남 / 경기 부천 등)일 수 있어, 학생증에 적힌 주소(강남구 일원동)와 맞는 곳을 고른다 */
    fun pickSchool(list: List<SchoolRef>): SchoolRef? =
        list.firstOrNull { it.address.contains("일원") } ?: list.firstOrNull { it.address.contains("강남") } ?: list.singleOrNull()

    fun findSchools(f: Fetcher, name: String): List<SchoolRef> = findSchoolsDetailed(f, name).first

    fun findSchoolsDetailed(f: Fetcher, name: String): Pair<List<SchoolRef>, String> {
        // 서울(B10) 먼저 — 결과가 적어 한 쪽에 다 들어온다. 못 찾으면 전국에서.
        var r = fetchAll(f, "schoolInfo", mapOf("SCHUL_NM" to name, "ATPT_OFCDC_SC_CODE" to "B10"))
        var list = schoolRefs(r, name)
        var note = r.note
        if (list.isEmpty()) {
            r = fetchAll(f, "schoolInfo", mapOf("SCHUL_NM" to name))
            list = schoolRefs(r, name)
            if (r.note.isNotEmpty()) note = r.note
        }
        return list to note
    }

    private fun schoolRefs(r: Fetched, name: String): List<SchoolRef> {
        return r.rows.map {
            SchoolRef(it.optString("ATPT_OFCDC_SC_CODE"), it.optString("SD_SCHUL_CODE"), it.optString("SCHUL_NM"), it.optString("ORG_RDNMA"), it.optString("SCHUL_KND_SC_NM"))
        }.filter { it.name == name && it.schoolCode.isNotEmpty() }
    }

    // ───────── 급식 ─────────

    private val ALLERGY = Regex("\\(([0-9.]+)\\)")

    /** "쌀밥<br/>된장국 (5.6.9)<br/>김치 (9)" → 메뉴 이름들 + 알레르기 번호들 */
    fun splitDishes(raw: String): Pair<List<String>, List<Int>> {
        val allergies = sortedSetOf<Int>()
        val dishes = raw.split(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)).map { line ->
            ALLERGY.findAll(line).forEach { m -> m.groupValues[1].split('.').mapNotNull { it.toIntOrNull() }.forEach { allergies.add(it) } }
            line.replace(ALLERGY, "").trim()
        }.filter { it.isNotEmpty() }
        return dishes to allergies.toList()
    }

    fun mealPlan(rows: List<JSONObject>): MealPlan {
        val byDay = linkedMapOf<String, MutableList<Meal>>()
        rows.sortedWith(compareBy({ it.optString("MLSV_YMD") }, { it.optString("MMEAL_SC_CODE") })).forEach {
            val (dishes, allergies) = splitDishes(it.optString("DDISH_NM"))
            byDay.getOrPut(it.optString("MLSV_YMD")) { ArrayList() }.add(Meal(it.optString("MMEAL_SC_NM"), dishes, it.optString("CAL_INFO"), allergies))
        }
        return MealPlan(byDay.map { (d, m) -> DayMeals(d, m) })
    }

    fun meals(f: Fetcher, school: SchoolRef, days: List<LocalDate>): MealPlan {
        val base = mapOf("ATPT_OFCDC_SC_CODE" to school.officeCode, "SD_SCHUL_CODE" to school.schoolCode)
        // 키 없이는 건수가 적게 제한되므로 하루씩 받는다 (하루치는 최대 3건)
        val rows = ArrayList<JSONObject>()
        var failNote = ""
        var anyOk = false
        days.forEach { d ->
            val one = fetchAll(f, "mealServiceDietInfo", base + mapOf("MLSV_YMD" to ymd(d)))
            rows.addAll(one.rows)
            if (one.complete) anyOk = true else if (failNote.isEmpty()) failNote = one.note
        }
        if (!anyOk && rows.isEmpty()) throw java.io.IOException("급식을 불러오지 못했어요 · $failNote")
        return mealPlan(rows)
    }

    // ───────── 시간표 (컴시간을 못 쓸 때의 대체) ─────────

    fun timetablePlan(rows: List<JSONObject>, grade: Int, cls: Int): Timetable {
        val byDay = sortedMapOf<String, MutableList<Lesson>>()
        rows.forEach {
            val p = it.optString("PERIO").toIntOrNull() ?: return@forEach
            byDay.getOrPut(it.optString("ALL_TI_YMD")) { ArrayList() }.add(Lesson(p, it.optString("ITRT_CNTNT").trim()))
        }
        return Timetable("NEIS", grade, cls, byDay.map { (d, l) -> DayTimetable(d, l.distinctBy { it.period }.sortedBy { it.period }) })
    }

    /** 학교 종류에 맞는 시간표 엔드포인트 (중학교 misTimetable, 고등학교 hisTimetable, 초등학교 elsTimetable) */
    fun timetableEndpoint(kind: String) = when {
        kind.contains("고등") -> "hisTimetable"
        kind.contains("초등") -> "elsTimetable"
        else -> "misTimetable"
    }

    fun timetable(f: Fetcher, school: SchoolRef, grade: Int, cls: Int, days: List<LocalDate>): Timetable {
        val ep = timetableEndpoint(school.kind)
        val base = mapOf("ATPT_OFCDC_SC_CODE" to school.officeCode, "SD_SCHUL_CODE" to school.schoolCode, "GRADE" to "$grade", "CLASS_NM" to "$cls")
        // 하루씩 받고(쪽 나눠서), 쪽을 못 넘기면 그날만 교시별로 한 건씩
        val rows = ArrayList<JSONObject>()
        var failNote = ""
        var anyOk = false
        days.forEach { d ->
            val day = fetchAll(f, ep, base + mapOf("ALL_TI_YMD" to ymd(d)))
            if (day.complete) { rows.addAll(day.rows); anyOk = true; return@forEach }
            if (failNote.isEmpty()) failNote = day.note
            for (p in 1..8) {
                val one = fetchAll(f, ep, base + mapOf("ALL_TI_YMD" to ymd(d), "PERIO" to "$p"))
                rows.addAll(one.rows)
                if (one.complete) anyOk = true
                if (one.rows.isEmpty() && p > 1) break           // 그날 더 이상 수업이 없음
            }
        }
        if (!anyOk && rows.isEmpty()) throw java.io.IOException("시간표를 불러오지 못했어요 · $failNote")
        return timetablePlan(rows, grade, cls)
    }
}
