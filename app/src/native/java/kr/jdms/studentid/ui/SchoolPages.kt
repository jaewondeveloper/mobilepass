package kr.jdms.studentid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kr.jdms.studentid.school.MealPlan
import kr.jdms.studentid.school.SchoolRepository
import kr.jdms.studentid.school.Timetable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private fun dayLabel(ymd: String): String {
    val d = runCatching { LocalDate.parse(ymd, DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull() ?: return ymd
    return "${d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)} · ${d.monthValue}월 ${d.dayOfMonth}일"
}

/** 나이스 알레르기 번호표 */
private val ALLERGY_NAMES = mapOf(
    1 to "난류", 2 to "우유", 3 to "메밀", 4 to "땅콩", 5 to "대두", 6 to "밀", 7 to "고등어", 8 to "게", 9 to "새우", 10 to "돼지고기",
    11 to "복숭아", 12 to "토마토", 13 to "아황산류", 14 to "호두", 15 to "닭고기", 16 to "쇠고기", 17 to "오징어", 18 to "조개류", 19 to "잣",
)

/** 페이지가 열리는 애니메이션(450ms)이 끝난 뒤에 내용을 채운다. 움직이는 중에 큰 화면을 한꺼번에 그리면 프레임이 떨어진다. */
private suspend fun <T> awaitAfterOpen(openedAt: Long, load: ((T) -> Unit) -> Unit): T {
    val d = CompletableDeferred<T>()
    load { d.complete(it) }
    val r = d.await()
    val wait = openedAt + Motion.PAGE_IN_MS + 40 - System.currentTimeMillis()
    if (wait > 0) delay(wait)
    return r
}

/** 카드를 한 번에 다 그리지 않고 몇 프레임에 하나씩 나눠 그린다 (처음 열 때 한 프레임이 길어지는 것을 막음) */
@Composable
private fun rememberReveal(total: Int, enabled: Boolean, key: Any?): Int {
    var n by remember(key) { mutableIntStateOf(if (enabled) 0 else total) }
    LaunchedEffect(key, total, enabled) {
        while (enabled && n < total) { withFrameNanos { }; withFrameNanos { }; n++ }
    }
    return if (enabled) n.coerceAtMost(total) else total
}

@Composable
fun TimetablePage(state: AppState, onBack: () -> Unit, onChangeClass: () -> Unit) {
    var result by remember { mutableStateOf<SchoolRepository.Loaded<Timetable>?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val openedAt = remember { System.currentTimeMillis() }
    LaunchedEffect(tick, state.classTick) {
        result = null
        result = awaitAfterOpen<SchoolRepository.Loaded<Timetable>>(openedAt) { cb -> state.school.loadTimetable(force = tick > 0, cb = cb) }
    }
    PageFrame("시간표", onBack) {
        TimetableBody(state.school.config.grade, state.school.config.cls, result, onChangeClass) { tick++ }
    }
}

/** 시간표 페이지 내용 (머리글 아래): 학년·반 줄, 요일별 카드 */
@Composable
fun TimetableBody(grade: Int, cls: Int, r: SchoolRepository.Loaded<Timetable>?, onChangeClass: () -> Unit, reveal: Boolean = true, onRetry: () -> Unit) {
    ListCard {
        ListRow(
            "${grade}학년 ${cls}반",
            desc = if (r?.value != null) "출처: ${r.value.source}${if (r.stale) " · 저장된 시간표" else ""} · 눌러서 바꾸기" else "눌러서 바꾸기",
            onClick = onChangeClass,
        )
    }
    Spacer(Modifier.height(20.dp))
    when {
        r == null -> ListCard { ListRow("불러오는 중이에요", "잠시만 기다려 주세요", descAccent = false) }
        r.value == null -> ListCard { ListRow("불러오지 못했어요", (r.error ?: "인터넷 연결을 확인해 주세요") + "\n눌러서 다시 시도해요", descAccent = false, onClick = onRetry) }
        else -> {
            val days = r.value.days.filter { it.lessons.isNotEmpty() }
            if (days.isEmpty()) ListCard { ListRow("이번 주 시간표가 아직 없어요", "학교에서 시간표를 올리면 보여요", descAccent = false) }
            val shown = rememberReveal(days.size, reveal, r)
            days.take(shown).forEach { d ->
                SectionLabel(dayLabel(d.ymd))
                ListCard {
                    d.lessons.forEachIndexed { i, l ->
                        ListRow("${l.period}교시  ${l.subject}", l.teacher.ifEmpty { null }, divider = i < d.lessons.size - 1, descAccent = false)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun MealPage(state: AppState, onBack: () -> Unit) {
    var result by remember { mutableStateOf<SchoolRepository.Loaded<MealPlan>?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val openedAt = remember { System.currentTimeMillis() }
    LaunchedEffect(tick) {
        result = null
        result = awaitAfterOpen<SchoolRepository.Loaded<MealPlan>>(openedAt) { cb -> state.school.loadMeals(force = tick > 0, cb = cb) }
    }
    PageFrame("급식", onBack) {
        ListCard {
            ListRow("이번 주 급식", "나이스 교육정보 · 눌러서 새로고침", onClick = { tick++ })
        }
        Spacer(Modifier.height(20.dp))
        val r = result
        when {
            r == null -> ListCard { ListRow("불러오는 중이에요", "잠시만 기다려 주세요", descAccent = false) }
            r.value == null -> ListCard { ListRow("불러오지 못했어요", (r.error ?: "인터넷 연결을 확인해 주세요") + "\n눌러서 다시 시도해요", descAccent = false, onClick = { tick++ }) }
            else -> {
                if (r.value.days.isEmpty()) ListCard { ListRow("이번 주 급식 정보가 없어요", "학교에서 식단을 올리면 보여요", descAccent = false) }
                val shown = rememberReveal(r.value.days.size, true, r)
                r.value.days.take(shown).forEach { d ->
                    SectionLabel(dayLabel(d.ymd))
                    ListCard {
                        d.meals.forEachIndexed { i, m ->
                            val title = if (m.calories.isNotEmpty()) "${m.type} · ${m.calories}" else m.type
                            val allergy = if (m.allergies.isEmpty()) "" else "\n알레르기: " + m.allergies.joinToString(", ") { ALLERGY_NAMES[it] ?: "$it" }
                            ListRow(title, m.dishes.joinToString("\n") + allergy, divider = i < d.meals.size - 1, descAccent = false)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

/**
 * 템플릿 숫자 선택(wx-number-picker)과 같은 모양의 돌려 고르는 선택기: 3줄이 보이고 가운데 줄이 선택, 놓으면 가운데로 딱 멈춘다.
 */
@Composable
fun WheelPicker(items: List<String>, index: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val itemH = 44.dp
    val itemPx = with(LocalDensity.current) { itemH.toPx() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = index.coerceIn(0, items.lastIndex))
    val fling = rememberSnapFlingBehavior(state)
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { moving ->
            if (!moving) onChange(state.firstVisibleItemIndex.coerceIn(0, items.lastIndex))
        }
    }
    val centered by remember { derivedStateOf { (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2f) 1 else 0).coerceIn(0, items.lastIndex) } }
    Box(modifier.height(itemH * 3)) {
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(itemH).clip(RoundedCornerShape(14.dp)).background(c.tile))
        LazyColumn(state = state, flingBehavior = fling, contentPadding = PaddingValues(vertical = itemH), modifier = Modifier.fillMaxSize()) {
            itemsIndexed(items) { i, s ->
                Box(Modifier.height(itemH).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val on = i == centered
                    T(s, if (on) 20 else 17, if (on) c.text else c.textMuted, if (on) 600 else 400, align = TextAlign.Center, single = true)
                }
            }
        }
    }
}

/** 학년·반 선택 (대화상자 안): 학년 1~3, 반 1~20 */
@Composable
fun ClassPicker(grade: Int, cls: Int, onGrade: (Int) -> Unit, onClass: (Int) -> Unit) {
    val c = LocalColors.current
    val grades = remember { (1..3).map { "${it}학년" } }
    val classes = remember { (1..20).map { "${it}반" } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { WheelPicker(grades, grade - 1, { onGrade(it + 1) }) }
        Column(Modifier.weight(1f)) { WheelPicker(classes, cls - 1, { onClass(it + 1) }) }
    }
}
