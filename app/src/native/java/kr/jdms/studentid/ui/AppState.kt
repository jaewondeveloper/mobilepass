package kr.jdms.studentid.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kr.jdms.studentid.CardStore

enum class Tab { HOME, CARD, MENU }
enum class Page { NONE, SETTINGS, TIPS, INFO, LICENSES, PRIVACY, TIMETABLE, MEAL, SCAN }
enum class Step { ID, CARD_FACE, SELFIE }

/** 화면이 읽는 앱 상태. 학생증/프로필 사진은 CardStore(기기 내부 저장소)가 원본이다. */
class AppState(context: Context, private val store: CardStore) {
    private val prefs = context.applicationContext.getSharedPreferences("sid_prefs", Context.MODE_PRIVATE)

    var tab by mutableStateOf(Tab.HOME)
    /** 열린 페이지 스택 (아래→위). 비어 있으면 탭 화면 */
    val stack = mutableStateListOf<Page>()
    val page: Page get() = stack.lastOrNull() ?: Page.NONE
    fun open(p: Page) { if (page != p) stack.add(p) }
    fun back() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    fun closeAll() { stack.clear() }
    /** 급식/시간표 (학년·반 설정, 나이스·컴시간 불러오기) */
    val school = kr.jdms.studentid.school.SchoolRepository(context)
    /** 학년/반이 바뀔 때마다 올라가서, 열려 있는 시간표가 다시 불러오게 한다 */
    var classTick by mutableStateOf(0)
    /** 화면 캡처 테스트용: 홈 시간표 줄을 이 값으로 고정해 보여 준다 */
    var stripPreview by mutableStateOf<Pair<String, List<Pair<Int, String>>>?>(null)
    var name by mutableStateOf<String?>(null)
    var code by mutableStateOf<String?>(null)
    var photo by mutableStateOf<ImageBitmap?>(null)

    val registered get() = name != null && code != null

    /** 데모(홍길동) 등록 상태. 실제 학교 데이터(나이스) 대신 '데모 버전'으로 표시하는 데 쓴다 */
    var demoMode by mutableStateOf(prefs.getBoolean("demo", false))
    /** 시작 화면을 한 번 넘기면(등록 전이라도) 이 실행 동안은 다시 보이지 않는다. 기기를 재부팅/재시작하면 초기화 */
    var gateDismissed by mutableStateOf(false)

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

    /** 시작 화면의 오른쪽 위 모서리를 연속으로 누르면: 홍길동으로 바로 등록된 것처럼 보여 준다 */
    fun enterDemo() {
        if (registered) return
        prefs.edit().putBoolean("demo", true).apply()
        demoMode = true
        gateDismissed = true
        register("홍길동", "S1234567", null)
    }

    /** 설정의 '데모 종료': 데모 등록을 지우고 시작 화면으로 되돌아간다 */
    fun exitDemo() {
        prefs.edit().putBoolean("demo", false).apply()
        demoMode = false
        gateDismissed = false
        unregister()
    }
}
