package kr.jdms.studentid.school

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SchoolTest {
    /** 주소(엔드포인트+쿼리)에 따라 정해진 응답을 돌려주는 가짜 네트워크 */
    private class Fake(val onGet: (String) -> String) : Fetcher {
        val urls = ArrayList<String>()
        override fun get(url: String, charset: String): String { synchronized(urls) { urls.add(url) }; return onGet(url) }
    }

    private fun neis(endpoint: String, total: Int, rows: List<String>) =
        """{"$endpoint":[{"head":[{"list_total_count":$total},{"RESULT":{"CODE":"INFO-000","MESSAGE":"정상 처리되었습니다."}}]},{"row":[${rows.joinToString(",")}]}]}"""

    private val none = """{"RESULT":{"CODE":"INFO-200","MESSAGE":"해당하는 데이터가 없습니다."}}"""
    private val school = SchoolRef("J10", "7530079", "중동중학교", "경기도 부천시", "중학교")

    @Test fun 주간은_월에서_금이고_주말이면_다음_주() {
        assertEquals(LocalDate.of(2026, 3, 2), Neis.schoolWeek(LocalDate.of(2026, 3, 4)).first())        // 수요일
        assertEquals(LocalDate.of(2026, 3, 6), Neis.schoolWeek(LocalDate.of(2026, 3, 4)).last())
        assertEquals(LocalDate.of(2026, 3, 9), Neis.schoolWeek(LocalDate.of(2026, 3, 7)).first())        // 토요일 → 다음 주
        assertEquals(LocalDate.of(2026, 3, 9), Neis.schoolWeek(LocalDate.of(2026, 3, 8)).first())        // 일요일
    }

    @Test fun 급식_메뉴에서_알레르기_번호를_분리한다() {
        val (dishes, allergies) = Neis.splitDishes("쌀밥<br/>된장국 (5.6.9)<br/>김치 (9)<br/>")
        assertEquals(listOf("쌀밥", "된장국", "김치"), dishes)
        assertEquals(listOf(5, 6, 9), allergies)
    }

    @Test fun 학교_찾기는_이름이_같은_학교만_고른다() {
        val rows = listOf(
            """{"ATPT_OFCDC_SC_CODE":"J10","SD_SCHUL_CODE":"7530079","SCHUL_NM":"중동중학교","SCHUL_KND_SC_NM":"중학교","ORG_RDNMA":"경기도 부천시"}""",
            """{"ATPT_OFCDC_SC_CODE":"J10","SD_SCHUL_CODE":"1","SCHUL_NM":"중동중학교부설","SCHUL_KND_SC_NM":"중학교","ORG_RDNMA":"x"}""",
        )
        val f = Fake { neis("schoolInfo", 2, rows) }
        val found = Neis.findSchools(f, "중동중학교")
        assertEquals(1, found.size)
        assertEquals("7530079", found[0].schoolCode)
        assertTrue(f.urls[0].contains("Type=json") && f.urls[0].contains("SCHUL_NM="))
    }

    @Test fun 급식은_하루씩_받아_날짜별로_묶는다() {
        val f = Fake { url ->
            when {
                url.contains("MLSV_YMD=20260302") -> neis("mealServiceDietInfo", 1, listOf("""{"MLSV_YMD":"20260302","MMEAL_SC_CODE":"2","MMEAL_SC_NM":"중식","DDISH_NM":"쌀밥<br/>국 (1.2)","CAL_INFO":"600.1 Kcal"}"""))
                url.contains("MLSV_YMD=20260303") -> neis("mealServiceDietInfo", 1, listOf("""{"MLSV_YMD":"20260303","MMEAL_SC_CODE":"2","MMEAL_SC_NM":"중식","DDISH_NM":"볶음밥","CAL_INFO":"700 Kcal"}"""))
                else -> none
            }
        }
        val plan = Neis.meals(f, school, Neis.schoolWeek(LocalDate.of(2026, 3, 4)))
        assertEquals(listOf("20260302", "20260303"), plan.days.map { it.ymd })
        assertEquals(listOf("쌀밥", "국"), plan.days[0].meals[0].dishes)
        assertEquals(listOf(1, 2), plan.days[0].meals[0].allergies)
        assertEquals(5, f.urls.size)
        assertTrue(f.urls.all { it.contains("pSize=5") })      // 키 없이는 5건까지만
    }

    @Test fun 급식_요청이_모두_실패하면_이유를_알려준다() {
        val f = Fake { throw java.io.IOException("timeout") }
        val e = runCatching { Neis.meals(f, school, Neis.schoolWeek(LocalDate.of(2026, 3, 4))) }.exceptionOrNull()
        assertTrue(e != null && e.message!!.contains("timeout"))
    }

    @Test fun 같은_이름_학교가_여러_곳이면_일원동_학교를_고른다() {
        val bucheon = SchoolRef("J10", "1", "중동중학교", "경기도 부천시 길주로", "중학교")
        val seoul = SchoolRef("B10", "2", "중동중학교", "서울특별시 강남구 일원로8길 37", "중학교")
        assertEquals("2", Neis.pickSchool(listOf(bucheon, seoul))!!.schoolCode)
    }

    @Test fun 나이스_시간표는_교시순으로_정리한다() {
        val rows = listOf(
            """{"ALL_TI_YMD":"20260302","PERIO":"2","ITRT_CNTNT":"수학"}""",
            """{"ALL_TI_YMD":"20260302","PERIO":"1","ITRT_CNTNT":"국어"}""",
            """{"ALL_TI_YMD":"20260303","PERIO":"1","ITRT_CNTNT":"영어"}""",
        )
        val f = Fake { neis("misTimetable", 3, rows) }
        val t = Neis.timetable(f, school, 2, 3, Neis.schoolWeek(LocalDate.of(2026, 3, 4)))
        assertEquals("NEIS", t.source)
        assertEquals(listOf("국어", "수학"), t.days[0].lessons.map { it.subject })
        assertEquals(listOf(1, 2), t.days[0].lessons.map { it.period })
    }

    @Test fun 시간표_하루가_5건을_넘으면_6교시부터_한_교시씩_받는다() {
        fun row(p: Int) = "{\"ALL_TI_YMD\":\"20260302\",\"PERIO\":\"$p\",\"ITRT_CNTNT\":\"과목$p\"}"
        val f = Fake { url ->
            when {
                url.contains("PERIO=6") -> neis("misTimetable", 1, listOf(row(6)))
                url.contains("PERIO=7") -> neis("misTimetable", 1, listOf(row(7)))
                url.contains("PERIO=") -> none
                url.contains("ALL_TI_YMD=20260302") -> neis("misTimetable", 7, (1..5).map { row(it) })     // 키 없이는 앞 5건만 온다
                else -> none
            }
        }
        val t = Neis.timetable(f, school, 2, 8, Neis.schoolWeek(LocalDate.of(2026, 3, 4)))
        assertEquals((1..7).toList(), t.days[0].lessons.map { it.period })
        assertEquals("과목7", t.days[0].lessons.last().subject)
    }

    /** CI 에서만(LIVE_NEIS=1) 실제 나이스로 2학년 8반 시간표와 급식을 받아 본다 */
    @Test fun 실제_나이스_확인() {
        if (System.getenv("LIVE_NEIS") == null) return
        val f = UrlFetcher(0)
        val (list, note) = Neis.findSchoolsDetailed(f, "중동중학교")
        val s = Neis.pickSchool(list) ?: error("학교 못 찾음 $note")
        val week = Neis.schoolWeek(LocalDate.now())
        val t = Neis.timetable(f, s, 2, 8, week)
        val m = Neis.meals(f, s, week)
        println("@@LIVE school=" + s.name + " " + s.officeCode + " " + s.schoolCode + " " + s.address)
        t.days.forEach { d -> println("@@LIVE tt " + d.ymd + " " + d.lessons.joinToString(",") { it.period.toString() + it.subject }) }
        m.days.forEach { d -> println("@@LIVE meal " + d.ymd + " " + d.meals.joinToString("|") { it.type + ":" + it.dishes.joinToString("/") }) }
        assertTrue(t.days.any { it.lessons.size >= 5 })
        assertTrue(m.days.isNotEmpty())
    }

    @Test fun 컴시간_페이지에서_주소_조각을_읽는다() {
        val page = "function school_ra(sc){ \$.ajax({ url:'./36179?17384l', type:'get' }); } ... sc_data('73629_',sc,1,'0')"
        val site = Comcigan(Fake { "" }).readSite(page)
        assertEquals("/36179", site.path)
        assertEquals("17384l", site.query)
        assertEquals("73629_", site.prefix)
    }

    @Test fun 컴시간_검색_결과와_시간표를_푼다() {
        val c = Comcigan(Fake { "" })
        val found = c.parseSearch("""{"학교검색":[[1,"경기","중동중학교",12345],[2,"부산","중동중학교",999]]}###""", "중동중학교")
        assertEquals(listOf("경기" to 12345, "부산" to 999), found)

        // 2학년 3반: 월요일 1교시 = 과목 1(수학)·교사 2(이교사), 2교시 = 과목 2(국어)·교사 1(김교사)
        val data = JSONObject("""{"분리":100,"자료492":["","수학","국어"],"자료481":["","김교사","이교사","박교사"]}""")
        fun arr(vararg v: Any) = org.json.JSONArray().also { a -> v.forEach { a.put(it) } }
        val monday = org.json.JSONArray(listOf(0, 102, 201, 0))
        val class3 = arr(org.json.JSONArray(), monday, org.json.JSONArray(listOf(0, 0, 0, 0)))       // 요일 0(안 씀), 월, 화
        val grade2 = arr(org.json.JSONArray(), org.json.JSONArray(), org.json.JSONArray(), class3)   // 반 0,1,2 는 비움, 3반
        data.put("자료147", arr(org.json.JSONArray(), org.json.JSONArray(), grade2))                  // 학년 0,1 은 비움, 2학년
        val t = c.parseTimetable(data, 2, 3, Neis.schoolWeek(LocalDate.of(2026, 3, 4)))
        assertEquals("컴시간", t.source)
        assertEquals(listOf("수학", "국어"), t.days[0].lessons.map { it.subject })
        assertEquals("이교사", t.days[0].lessons[0].teacher)
    }

    @Test fun 저장한_결과를_JSON으로_되살린다() {
        val t = Timetable("NEIS", 1, 2, listOf(DayTimetable("20260302", listOf(Lesson(1, "국어", "김")))))
        assertEquals(t, Timetable.fromJson(t.toJson()))
        val m = MealPlan(listOf(DayMeals("20260302", listOf(Meal("중식", listOf("밥", "국"), "600 Kcal", listOf(1, 5))))))
        assertEquals(m, MealPlan.fromJson(m.toJson()))
    }

    @Test fun 홈_시간표_날짜는_4시5분을_기준으로_바뀐다() {
        fun at(y: Int, m: Int, d: Int, h: Int, mi: Int) = java.time.LocalDateTime.of(y, m, d, h, mi)
        // 2026-03-04 는 수요일
        assertEquals(LocalDate.of(2026, 3, 4), TimetableView.target(at(2026, 3, 4, 0, 0)))      // 자정 이후 → 오늘
        assertEquals(LocalDate.of(2026, 3, 4), TimetableView.target(at(2026, 3, 4, 16, 4)))     // 4시 4분 → 오늘
        assertEquals(LocalDate.of(2026, 3, 5), TimetableView.target(at(2026, 3, 4, 16, 5)))     // 4시 5분 → 내일
        assertEquals(LocalDate.of(2026, 3, 5), TimetableView.target(at(2026, 3, 4, 23, 59)))    // 자정 전 → 내일
        assertEquals(LocalDate.of(2026, 3, 9), TimetableView.target(at(2026, 3, 6, 17, 0)))     // 금요일 저녁 → 월요일
        assertEquals(LocalDate.of(2026, 3, 9), TimetableView.target(at(2026, 3, 7, 9, 0)))      // 토요일 → 월요일
        assertEquals(LocalDate.of(2026, 3, 9), TimetableView.target(at(2026, 3, 8, 20, 0)))     // 일요일 → 월요일
        val today = LocalDate.of(2026, 3, 4)
        assertEquals("오늘 시간표", TimetableView.label(today, today))
        assertEquals("내일 시간표", TimetableView.label(today.plusDays(1), today))
        assertEquals("월요일 시간표", TimetableView.label(LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 6)))
    }
}
