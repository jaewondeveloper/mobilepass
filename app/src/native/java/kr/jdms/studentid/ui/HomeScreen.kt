package kr.jdms.studentid.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kr.jdms.studentid.R

/**
 * 홈/학생증 탭 머리글: 학교 이름 + 드롭다운 화살표. 원본처럼 (12,10) 142x48 알약 안에 글자는 x=24, 화살표는 x=120.
 * 누르면 0.979 로 줄고 물결이 깔리며, 학교 선택 메뉴가 열린다.
 */
@Composable
fun BoxScope.SchoolTitle(onClick: () -> Unit, glass: Boolean = false) {
    val c = LocalColors.current
    Box(Modifier.align(Alignment.TopStart).offset(12.dp, 10.dp).size(142.dp, 48.dp)) {
        GlassLayer(glass, 24.dp, Modifier.fillMaxSize())
        PressBox(Modifier.fillMaxSize(), shape = RoundedCornerShape(26.dp), scale = 0.979f, onClick = onClick) {
            PT("중동중학교", 12f, 13f, 18, c.text, 700)
            Ico(R.drawable.ic_dropdown, 24.dp, tint = c.text, modifier = Modifier.align(Alignment.TopStart).offset(108.dp, 12.dp))
        }
    }
}

/**
 * 학교 선택 메뉴 (원본 wx-context-menu): (10,4) 186x167, 둥근 26, 반투명 흰 배경,
 * 열릴 때 왼쪽 위에서 0.93 → 1 로 커지며 투명도 0.55 → 1.
 */
@Composable
fun SchoolMenu(open: Boolean, top: androidx.compose.ui.unit.Dp, onDismiss: () -> Unit) {
    val c = LocalColors.current
    var shown by remember { mutableStateOf(false) }
    val p = remember { Animatable(0f) }
    LaunchedEffect(open) {
        if (open) { shown = true; p.animateTo(1f, tween(200, easing = Motion.Dialog)) }
        else { p.animateTo(0f, tween(150, easing = androidx.compose.animation.core.LinearEasing)); shown = false }
    }
    if (!shown) return
    Box(Modifier.fillMaxSize().padding(top = top).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }) {
        // 뒤가 조금도 비치지 않도록 완전 불투명
        val bg = if (c.dark) Color(0xFF2A2A2D) else Color(0xFFFCFCFF)
        val sub = c.text.copy(alpha = 0.4f)
        Box(
            Modifier.align(Alignment.TopStart).offset(10.dp, 4.dp).size(186.dp, 167.dp)
                .graphicsLayer {
                    val s = 0.93f + 0.07f * p.value
                    scaleX = s; scaleY = s; alpha = 0.55f + 0.45f * p.value
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .cssShadow(26.dp, 20.dp, 8.dp, if (c.dark) Color(0x66000000) else Color(0x1A000000))
                .clip(RoundedCornerShape(26.dp)).background(bg)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        ) {
            // 현재 학교 (선택됨)
            PressBox(Modifier.offset(0.dp, 0.dp).fillMaxWidth().height(50.dp), shape = RoundedCornerShape(26.dp), onClick = onDismiss) {
                PT("중동중학교", 24f, 16.5f, 17, c.accentText, 600)
                Ico(R.drawable.ic_check, 20.dp, tint = c.accentText, modifier = Modifier.align(Alignment.TopStart).offset(142.dp, 18.dp))
            }
            // 다른 학교 (선택 불가: 40% 투명)
            Box(Modifier.offset(0.dp, 50.dp).fillMaxWidth().height(44.dp)) { PT("중동고등학교", 24f, 10.5f, 17, sub, 400) }
            // 점선
            Box(
                Modifier.offset(12.dp, 97.dp).width(162.dp).height(1.dp).drawBehind {
                    drawLine(sub, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())))
                },
            )
            // 장소 설정 (사용 불가)
            Box(Modifier.offset(0.dp, 109.dp).fillMaxWidth().height(54.dp)) {
                Ico(R.drawable.ic_settings, 24.dp, tint = sub, modifier = Modifier.align(Alignment.TopStart).offset(22.dp, 15.dp))
                PT("장소 설정", 54f, 16.5f, 16, sub, 400)
            }
        }
    }
}

/** 홈의 '내일/오늘 시간표' 줄에 쓸 (제목, 과목들). 학년·반이 없거나 아직 못 불러왔으면 null */
@Composable
fun rememberTimetableStrip(state: AppState): Pair<String, List<String>>? {
    state.stripPreview?.let { return it }
    var now by remember { mutableStateOf(java.time.LocalDateTime.now()) }
    LaunchedEffect(Unit) { if (IdTabConfig.liveClock) while (true) { kotlinx.coroutines.delay(30_000); now = java.time.LocalDateTime.now() } }
    val target = kr.jdms.studentid.school.TimetableView.target(now)
    var subjects by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(state.classTick, target) {
        if (!state.school.config.isSet) { subjects = null; return@LaunchedEffect }
        state.school.loadTimetable(target) { r ->
            val ymd = kr.jdms.studentid.school.Neis.ymd(target)
            subjects = r.value?.days?.firstOrNull { it.ymd == ymd }?.lessons?.map { it.subject }
        }
    }
    val s = subjects
    if (s.isNullOrEmpty() || !state.school.config.isSet) return null
    return kr.jdms.studentid.school.TimetableView.label(target, now.toLocalDate()) to s
}

@Composable
fun HomeTab(state: AppState, onScan: () -> Unit, onOpenCard: () -> Unit, onSoon: () -> Unit, onMeal: () -> Unit = onSoon, onTimetable: () -> Unit = onSoon, sc: androidx.compose.foundation.ScrollState = rememberScrollState()) {
    val c = LocalColors.current
    val strip = rememberTimetableStrip(state)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().scrollEdgeFade(sc).verticalScroll(sc)) {
            Box(Modifier.fillMaxWidth().height(if (strip != null) (481f + 33f + (strip.second.chunked(4).size) * 48f + 77f + 150f).dp else 660.dp)) {

                // '지금' 카드: (10,74) 373x147, 둥근 30
                PutFull(74f, 147f, modifier = Modifier.glass(c)) {
                    PT(if (state.registered) "학생증이 등록되었어요" else "학생증을 등록해 주세요", 19f, 13f, 16, c.textBody, 600)
                    PT(
                        if (state.registered) "${state.name} · ${state.code}" else "종이 학생증을 스캔하면 모바일 학생증이 만들어져요.",
                        18f, 48f, 14, c.body2,
                    )
                    PillButton(
                        if (state.registered) "학생증 보기" else "학생증 스캔하기",
                        onClick = if (state.registered) onOpenCard else onScan,
                        modifier = Modifier.align(Alignment.TopStart).offset(16.dp, 93.dp),
                        height = 38.dp, bg = c.btnBg, border = c.btnBorder, fontSize = 13, icon = R.drawable.ic_scan, scale = 0.975f,
                    )
                }
                PT("학교생활", 20f, 273f, 13, c.text, 600)

                // 대시보드 카드: (10,300) 373x171, 둥근 28. 어디를 눌러도 카드 전체가 0.992 로 줄어든다.
                PutFull(300f, 171f, modifier = Modifier.pressScaleObserver(0.992f).clip(CardShape).background(c.card)) {
                    PT("대시보드", 16f, 9f, 13, c.textSub, 600)
                    Column(
                        Modifier.align(Alignment.TopStart).offset(y = 33.dp).fillMaxWidth().padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val tiles = listOf(
                            Triple(R.drawable.ic_attend, "출결", onSoon),
                            Triple(R.drawable.ic_meal, "급식", onMeal),
                            Triple(R.drawable.ic_timetable, "시간표", onTimetable),
                            Triple(R.drawable.ic_notice, "공지", onSoon),
                            Triple(R.drawable.ic_profile, if (state.registered) "학생증 등록" else "학생증 없음", onOpenCard),
                            Triple(R.drawable.ic_scan, "스캔", onScan),
                        )
                        tiles.chunked(4).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                row.forEach { (icon, label, click) -> Tile(icon, label, click, Modifier.weight(1f)) }
                                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                            }
                        }
                    }
                }

                // 시간표 카드: 학교생활 카드 바로 아래. 큰 카드 안에 과목 이름이 둥근 칸 하나씩 (대시보드 칸과 같은 모양, 4열)
                if (strip != null) {
                    val rows = strip.second.chunked(4)
                    val h = 33f + rows.size * 48f + (rows.size - 1) * 4f + 8f
                    PutFull(481f, h, modifier = Modifier.clip(SmoothRoundedShape(28.dp)).background(c.card)) {
                        PT(strip.first, 16f, 9f, 13, c.textSub, 600)
                        Column(
                            Modifier.align(Alignment.TopStart).offset(y = 33.dp).fillMaxWidth().padding(horizontal = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            rows.forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    row.forEach { name ->
                                        Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(22.dp)).background(c.tile), contentAlignment = Alignment.Center) {
                                            T(name, 15, c.text, 600, maxLines = 1, softWrap = false, single = true)
                                        }
                                    }
                                    repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }

                // 학생증 한 줄: (10,481) 373x77, 둥근 28. 누르면 0.96 으로 줄고 글자 묶음은 0.979 더 줄어든다.
                PutFull(if (strip != null) 481f + (33f + (strip.second.chunked(4).size) * 48f + (strip.second.chunked(4).size - 1) * 4f + 8f) + 10f else 481f, 77f) {
                    PressBox(Modifier.fillMaxSize(), shape = CardShape, scale = 0.96f, bg = c.card, onClick = onOpenCard) {
                        val ts by animateFloatAsState(if (LocalPressed.current) 0.979f else 1f, tween(Motion.RECOIL_MS, easing = Motion.Recoil), label = "cardText")
                        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = ts; scaleY = ts; transformOrigin = TransformOrigin(0.5f, 0.5f) }) {
                            PT("중동중학교", 16f, 15.5f, 12, c.text, 600, lh = 16)
                            PT("모바일 학생증", 16f, 31.5f, 12, c.text, 600, lh = 16)
                            PT(state.name ?: "미등록", 16f, 47.5f, 12, c.textMuted, 600, lh = 16)
                        }
                        AvatarBox(state, Modifier.align(Alignment.TopEnd).offset((-9.5).dp, 9.5.dp))
                    }
                }
            }
        }
    }
}

/** 대시보드 칸: 86.3x63, 둥근 22, 아이콘 32 (위에서 5), 글자는 위에서 41 */
@Composable
private fun Tile(icon: Int, label: String, onClick: () -> Unit, modifier: Modifier) {
    val c = LocalColors.current
    Box(
        modifier.height(63.dp).clip(RoundedCornerShape(22.dp)).background(c.tile)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        Ico(icon, 32.dp, modifier = Modifier.align(Alignment.TopCenter).offset(y = 5.dp))
        T(label, 12, c.textSub, 600, align = TextAlign.Center, maxLines = 1, softWrap = false, single = true, modifier = Modifier.align(Alignment.TopCenter).offset(y = 41.dp))
    }
}

/** 학생증 카드의 프로필 자리 56x56, 둥근 20, 검정 5% 배경. 내 사진이 있으면 사진, 없으면 기본 아이콘(40) */
@Composable
fun AvatarBox(state: AppState, modifier: Modifier, onClick: (() -> Unit)? = null) {
    val photo = state.photo
    Box(
        modifier.size(56.dp).clip(RoundedCornerShape(20.dp)).background(Color(0x0D000000))
            .let { if (onClick != null && photo != null) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Image(photo, "프로필 사진", Modifier.size(56.dp), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        } else {
            Ico(R.drawable.ic_profile, 40.dp)
        }
    }
}
