package kr.jdms.studentid.school

import org.json.JSONArray
import org.json.JSONObject

/** 나이스에서 찾은 학교 */
data class SchoolRef(val officeCode: String, val schoolCode: String, val name: String, val address: String, val kind: String)

data class Lesson(val period: Int, val subject: String, val teacher: String = "")
data class DayTimetable(val ymd: String, val lessons: List<Lesson>)

/** source: "컴시간" 또는 "NEIS" */
data class Timetable(val source: String, val grade: Int, val cls: Int, val days: List<DayTimetable>) {
    fun toJson(): JSONObject = JSONObject()
        .put("source", source).put("grade", grade).put("cls", cls)
        .put("days", JSONArray().also { arr ->
            days.forEach { d ->
                arr.put(JSONObject().put("ymd", d.ymd).put("lessons", JSONArray().also { la ->
                    d.lessons.forEach { l -> la.put(JSONObject().put("period", l.period).put("subject", l.subject).put("teacher", l.teacher)) }
                }))
            }
        })

    companion object {
        fun fromJson(o: JSONObject): Timetable {
            val days = ArrayList<DayTimetable>()
            val da = o.getJSONArray("days")
            for (i in 0 until da.length()) {
                val d = da.getJSONObject(i)
                val la = d.getJSONArray("lessons")
                val lessons = ArrayList<Lesson>()
                for (j in 0 until la.length()) {
                    val l = la.getJSONObject(j)
                    lessons.add(Lesson(l.getInt("period"), l.getString("subject"), l.optString("teacher")))
                }
                days.add(DayTimetable(d.getString("ymd"), lessons))
            }
            return Timetable(o.getString("source"), o.getInt("grade"), o.getInt("cls"), days)
        }
    }
}

/** 한 끼: type = 조식/중식/석식, dishes = 알레르기 표시를 뗀 메뉴 이름들, allergies = 알레르기 번호(중복 없이) */
data class Meal(val type: String, val dishes: List<String>, val calories: String, val allergies: List<Int>)
data class DayMeals(val ymd: String, val meals: List<Meal>)

data class MealPlan(val days: List<DayMeals>) {
    fun toJson(): JSONObject = JSONObject().put("days", JSONArray().also { arr ->
        days.forEach { d ->
            arr.put(JSONObject().put("ymd", d.ymd).put("meals", JSONArray().also { ma ->
                d.meals.forEach { m ->
                    ma.put(JSONObject().put("type", m.type).put("dishes", JSONArray(m.dishes)).put("calories", m.calories).put("allergies", JSONArray(m.allergies)))
                }
            }))
        }
    })

    companion object {
        fun fromJson(o: JSONObject): MealPlan {
            val days = ArrayList<DayMeals>()
            val da = o.getJSONArray("days")
            for (i in 0 until da.length()) {
                val d = da.getJSONObject(i)
                val ma = d.getJSONArray("meals")
                val meals = ArrayList<Meal>()
                for (j in 0 until ma.length()) {
                    val m = ma.getJSONObject(j)
                    val dishes = m.getJSONArray("dishes").let { a -> List(a.length()) { a.getString(it) } }
                    val al = m.getJSONArray("allergies").let { a -> List(a.length()) { a.getInt(it) } }
                    meals.add(Meal(m.getString("type"), dishes, m.optString("calories"), al))
                }
                days.add(DayMeals(d.getString("ymd"), meals))
            }
            return MealPlan(days)
        }
    }
}
