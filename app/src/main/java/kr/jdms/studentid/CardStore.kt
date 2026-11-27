package kr.jdms.studentid

import android.content.Context
import kr.jdms.studentid.scan.CardParser
import org.json.JSONObject

/** 등록된 학생증 정보를 앱 전용 저장소에 보관 (백업 비활성화, 다른 앱에서 접근 불가). */
class CardStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("student_card", Context.MODE_PRIVATE)

    fun save(name: String, code: String): Boolean {
        val c = CardParser.normalizeCode(code) ?: return false
        val n = name.trim()
        if (n.length !in 1..20) return false
        prefs.edit().putString(KEY_NAME, n).putString(KEY_CODE, c).apply()
        return true
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    /** JS에 넘길 JSON. 없으면 null. */
    fun toJson(): String? {
        val n = prefs.getString(KEY_NAME, null) ?: return null
        val c = prefs.getString(KEY_CODE, null) ?: return null
        return JSONObject().put("name", n).put("code", c).toString()
    }

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_CODE = "code"
    }
}
