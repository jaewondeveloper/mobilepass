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
        override fun get(url: String, charset: String): String { urls.add(url); return onGet(url) }
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

    @Test fun 급식은_한_번에_받아_날짜별로_묶는다() {
        val rows = listOf(
            """{"MLSV_YMD":"20260302","MMEAL_SC_CODE":"2","MMEAL_SC_NM":"중식","DDISH_NM":"쌀밥<br/>국 (1.2)","CAL_INFO":"600.1 Kcal"}""",
            """{"MLSV_YMD":"20260303","MMEAL_SC_CODE":"2","MMEAL_SC_NM":"중식","DDISH_NM":"볶음밥","CAL_INFO":"700 Kcal"}""",
        )
        val f = Fake { neis("mealServiceDietInfo", 2, rows) }
        val plan = Neis.meals(f, school, Neis.schoolWeek(LocalDate.of(2026, 3, 4)))
        assertEquals(2, plan.days.size)
        assertEquals(listOf("쌀밥", "국"), plan.days[0].meals[0].dishes)
        assertEquals(listOf(1, 2), plan.days[0].meals[0].allergies)
        assertEquals(1, f.urls.size)
    }

    @Test fun 급식_건수_제한이_있으면_하루씩_다시_요청한다() {
        // 범위 요청은 5건만 주고 total 은 5 보다 크다고 하며 2쪽은 오류 → 하루씩 받아야 한다
        val f = Fake { url ->
            when {
                url.contains("MLSV_FROM_YMD") && url.contains("pIndex=1") -> neis("mealServiceDietInfo", 9, (1..5).map { """{"MLSV_YMD":"20260302","MMEAL_SC_CODE":"2","MMEAL_SC_NM":"중식","DDISH_NM":"a"}""" })
                url.contains("MLSV_FROM_YMD") -> """{"RESULT":{"CODE":"ERROR-337","MESSAGE":"일 트래픽 제한"}}"""
                url.contains("MLSV_YMD=20260302") -> neis("mealServiceDietInfo", 1, listOf("""{"MLSV_YMD":"20260302","MMEAL_SC_CODE":"2","MMEAL_SC_NM":"중식","DDISH_NM":"월요일밥"}"""))
                url.contains("MLSV_YMD=20260303") -> neis("mealServiceDietInfo", 1, listOf("""{"MLSV_YMD":"20260303","MMEAL_SC_CODE":"2","MMEAL_SC_NM":"중식","DDISH_NM":"화요일밥"}"""))
                else -> none
            }
        }
        val plan = Neis.meals(f, school, Neis.schoolWeek(LocalDate.of(2026, 3, 4)))
        assertEquals(listOf("20260302", "20260303"), plan.days.map { it.ymd })
        assertEquals("월요일밥", plan.days[0].meals[0].dishes[0])
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
}
