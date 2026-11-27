package kr.jdms.studentid

import android.content.Context
import android.util.Base64
import java.io.File
import kr.jdms.studentid.scan.CardParser
import kr.jdms.studentid.scan.CardValidity
import kr.jdms.studentid.scan.ScanSession
import org.json.JSONObject

/** 등록된 학생증 정보를 앱 전용 저장소에 보관 (백업 비활성화, 다른 앱에서 접근 불가). */
class CardStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("student_card", Context.MODE_PRIVATE)
    private val profileFile get() = File(appContext.filesDir, "profile.png")

    fun save(name: String, code: String, expiry: String? = ScanSession.expiry): Boolean {
        val c = CardParser.normalizeCode(code) ?: return false
        val n = name.trim()
        if (n.length !in 1..20) return false
        prefs.edit().putString(KEY_NAME, n).putString(KEY_CODE, c).putString(KEY_EXPIRY, expiry ?: "").apply()
        ScanSession.clear()
        return true
    }

    fun clear() {
        prefs.edit().clear().apply()
        clearProfile()
    }

    /** 프로필 사진(배경 제거한 PNG)을 앱 전용 저장소에 저장. 임시 파일에 쓴 뒤 교체해서 중간에 끊겨도 깨지지 않게 한다. */
    fun saveProfile(png: ByteArray): Boolean = try {
        val tmp = File(appContext.filesDir, "profile.png.tmp")
        tmp.writeBytes(png)
        tmp.renameTo(profileFile) || run { profileFile.delete(); tmp.renameTo(profileFile) }
    } catch (e: Exception) {
        false
    }

    fun clearProfile() {
        runCatching { profileFile.delete() }
    }

    fun profileBytes(): ByteArray? = runCatching { profileFile.takeIf { it.exists() }?.readBytes() }.getOrNull()

    /** 웹에서 바로 <img src> 로 쓸 수 있는 data URL. 없으면 null */
    fun profileDataUrl(): String? {
        val f = profileFile
        if (!f.exists()) return null
        return try {
            "data:image/png;base64," + Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    /** 유효기간이 지났으면 등록 정보와 프로필 사진을 지운다. 지웠으면 true */
    fun purgeIfExpired(): Boolean {
        if (prefs.getString(KEY_NAME, null) == null) return false
        if (!CardValidity.isExpired(prefs.getString(KEY_EXPIRY, null))) return false
        clear()
        return true
    }

    fun load(): Pair<String, String>? {
        purgeIfExpired()
        val n = prefs.getString(KEY_NAME, null) ?: return null
        val c = prefs.getString(KEY_CODE, null) ?: return null
        return n to c
    }

    /** JS에 넘길 JSON. 없으면 null. */
    fun toJson(): String? {
        purgeIfExpired()
        val n = prefs.getString(KEY_NAME, null) ?: return null
        val c = prefs.getString(KEY_CODE, null) ?: return null
        return JSONObject().put("name", n).put("code", c).toString()
    }

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_CODE = "code"
        const val KEY_EXPIRY = "expiry"
    }
}
