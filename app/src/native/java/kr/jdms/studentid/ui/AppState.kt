package kr.jdms.studentid.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kr.jdms.studentid.CardStore

enum class Tab { HOME, CARD, MENU }
enum class Page { NONE, SETTINGS, TIPS, INFO, LICENSES, SCAN }
enum class Step { ID, CARD_FACE, SELFIE }

/** 화면이 읽는 앱 상태. 학생증/프로필 사진은 CardStore(기기 내부 저장소)가 원본이다. */
class AppState(context: Context, private val store: CardStore) {
    private val prefs = context.applicationContext.getSharedPreferences("sid_prefs", Context.MODE_PRIVATE)

    var tab by mutableStateOf(Tab.HOME)
    var page by mutableStateOf(Page.NONE)
    var name by mutableStateOf<String?>(null)
    var code by mutableStateOf<String?>(null)
    var photo by mutableStateOf<ImageBitmap?>(null)

    val registered get() = name != null && code != null

    // 설정 토글 (웹 버전의 PREF 와 같은 항목)
    var notifyAttendance by mutableStateOf(prefs.getBoolean("att", true))
    var notifyMeal by mutableStateOf(prefs.getBoolean("meal", true))
    var notifyNotice by mutableStateOf(prefs.getBoolean("notice", false))
    var brightBoost by mutableStateOf(prefs.getBoolean("bright", true))

    fun setToggle(key: String, v: Boolean) {
        prefs.edit().putBoolean(key, v).apply()
        when (key) { "att" -> notifyAttendance = v; "meal" -> notifyMeal = v; "notice" -> notifyNotice = v; "bright" -> brightBoost = v }
    }

    init { reload() }

    fun reload() {
        val c = store.load()
        name = c?.first
        code = c?.second
        photo = store.profileBytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }

    fun register(name: String, code: String, portraitPng: ByteArray?) {
        store.save(name, code)
        if (portraitPng != null) store.saveProfile(portraitPng)
        reload()
    }

    fun unregister() {
        store.clear()
        reload()
    }
}
