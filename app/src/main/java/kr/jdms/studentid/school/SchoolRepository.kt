package kr.jdms.studentid.school

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.Executors

/** 학년/반과 찾은 학교 정보를 기기에 보관 */
class SchoolPrefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("sid_school", Context.MODE_PRIVATE)
    var grade: Int get() = p.getInt("grade", 0); set(v) { p.edit().putInt("grade", v).apply() }
    var cls: Int get() = p.getInt("cls", 0); set(v) { p.edit().putInt("cls", v).apply() }
    val isSet: Boolean get() = grade in 1..9 && cls in 1..40
    fun school(): SchoolRef? {
        val s = p.getString("school", null) ?: return null
        return runCatching { JSONObject(s).let { SchoolRef(it.getString("o"), it.getString("c"), it.getString("n"), it.optString("a"), it.optString("k")) } }.getOrNull()
    }
    fun saveSchool(s: SchoolRef) {
        p.edit().putString("school", JSONObject().put("o", s.officeCode).put("c", s.schoolCode).put("n", s.name).put("a", s.address).put("k", s.kind).toString()).apply()
    }
    fun cache(key: String): Pair<Long, String>? {
        val t = p.getLong("cache_${key}_t", 0L); val v = p.getString("cache_$key", null) ?: return null
        return t to v
    }
    fun putCache(key: String, json: String) { p.edit().putLong("cache_${key}_t", System.currentTimeMillis()).putString("cache_$key", json).apply() }
    fun clearCache() { p.edit().apply { p.all.keys.filter { it.startsWith("cache_") }.forEach { remove(it) } }.apply() }
}

/**
 * 시간표/급식을 불러오는 한 곳. 결과는 기기에 저장해 두었다가(6시간) 같은 주에는 다시 요청하지 않고,
 * 네트워크가 안 될 때는 마지막으로 받은 것을 대신 보여 준다. 요청은 한 번에 하나씩 차례로 보낸다.
 */
class SchoolRepository(context: Context, private val fetcher: Fetcher = UrlFetcher()) {
    private val prefs = SchoolPrefs(context)
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    class Loaded<T>(val value: T?, val stale: Boolean, val error: String?)

    val config: SchoolPrefs get() = prefs

    private fun <T> bg(cb: (Loaded<T>) -> Unit, work: () -> Loaded<T>) {
        io.execute {
            val r = try { work() } catch (e: Exception) { Loaded<T>(null, false, e.message ?: "불러오지 못했어요") }
            main.post { cb(r) }
        }
    }

    /** 학교 정보(나이스 코드)를 아직 모르면 이름으로 찾아 저장한다 */
    private fun school(): SchoolRef {
        // 예전에 다른 지역의 같은 이름 학교가 저장돼 있었다면 버리고 다시 찾는다
        prefs.school()?.let { if (it.address.isEmpty() || it.address.contains("일원") || it.address.contains("강남")) return it }
        val (list, note) = Neis.findSchoolsDetailed(fetcher, SCHOOL_NAME)
        val s = Neis.pickSchool(list) ?: throw IllegalStateException("나이스에서 $SCHOOL_NAME 를 찾지 못했어요" + if (note.isNotEmpty()) " · $note" else "")
        prefs.saveSchool(s)
        return s
    }

    /** 컴시간 서버 주소 후보를 차례로 시도 (앞의 것이 막히거나 바뀌었으면 다음 것) */
    private fun comcigan(s: SchoolRef, days: List<LocalDate>): Timetable {
        var last: Exception? = null
        for (host in COMCI_HOSTS) {
            try {
                val t = Comcigan(fetcher, host).timetable(s.name, s.address, prefs.grade, prefs.cls, days)
                if (t.days.all { it.lessons.isEmpty() }) throw IllegalStateException("빈 시간표")
                return t
            } catch (e: Exception) { last = e }
        }
        throw last ?: IllegalStateException("컴시간 실패")
    }

    private fun weekKey(days: List<LocalDate>) = Neis.ymd(days.first())

    fun loadTimetable(today: LocalDate = LocalDate.now(), force: Boolean = false, cb: (Loaded<Timetable>) -> Unit) = bg(cb) {
        val days = Neis.schoolWeek(today)
        val key = "tt_${prefs.grade}_${prefs.cls}_${weekKey(days)}"
        val cached = prefs.cache(key)
        if (!force && cached != null && System.currentTimeMillis() - cached.first < TTL_MS) {
            return@bg Loaded(Timetable.fromJson(JSONObject(cached.second)), false, null)
        }
        try {
            val s = school()
            val t = try {
                comcigan(s, days)
            } catch (e: Exception) {
                Neis.timetable(fetcher, s, prefs.grade, prefs.cls, days)        // 컴시간을 못 읽으면 나이스 시간표로
            }
            prefs.putCache(key, t.toJson().toString())
            Loaded(t, false, null)
        } catch (e: Exception) {
            if (cached != null) Loaded(Timetable.fromJson(JSONObject(cached.second)), true, null) else throw e
        }
    }

    fun loadMeals(today: LocalDate = LocalDate.now(), force: Boolean = false, cb: (Loaded<MealPlan>) -> Unit) = bg(cb) {
        val days = Neis.schoolWeek(today)
        val key = "meal_${weekKey(days)}"
        val cached = prefs.cache(key)
        if (!force && cached != null && System.currentTimeMillis() - cached.first < TTL_MS) {
            return@bg Loaded(MealPlan.fromJson(JSONObject(cached.second)), false, null)
        }
        try {
            val m = Neis.meals(fetcher, school(), days)
            prefs.putCache(key, m.toJson().toString())
            Loaded(m, false, null)
        } catch (e: Exception) {
            if (cached != null) Loaded(MealPlan.fromJson(JSONObject(cached.second)), true, null) else throw e
        }
    }

    private companion object {
        const val SCHOOL_NAME = "중동중학교"
        const val TTL_MS = 6L * 60 * 60 * 1000
        val COMCI_HOSTS = listOf("http://comci.kr:4082", "http://xn--s39aj90b0nb2xw6xh.kr:4082", "http://comci.net:4082")
    }
}
