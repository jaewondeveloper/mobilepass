package kr.jdms.studentid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 뒤로가기 머리글(높이 68) 아래에 내용이 이어지는 페이지 틀 */
@Composable
private fun PageFrame(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    // 머리글은 스크롤 위에 떠 있고(내용이 그 뒤로 지나감), 스크롤되면 뒤로가기 버튼 뒤에 흐린 유리 배경이 생긴다
    val c = LocalColors.current
    val sc = rememberScrollState()
    val layer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val backdrop = remember(layer) { Backdrop(layer) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().recordBackdrop(backdrop).background(c.pageBg)) {
            Column(Modifier.fillMaxSize().scrollEdgeFade(sc).verticalScroll(sc).padding(top = 68.dp, bottom = 24.dp)) { content() }
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalBackdrop provides backdrop) {
            BackHeader(title, onBack, glass = sc.value > 0)
        }
    }
}

@Composable
fun SettingsPage(state: AppState, onBack: () -> Unit, onReRegister: () -> Unit, onUnregister: () -> Unit) {
    val view = androidx.compose.ui.platform.LocalView.current
    // 토글을 바꿀 때 진동 (삼성은 전용 스위치 진동, 그 외에는 확인/거절 진동)
    fun flip(key: String, old: Boolean) {
        kr.jdms.studentid.SwitchHaptic.perform(view, old, !old)
        state.setToggle(key, !old)
    }
    PageFrame("설정", onBack) {
        ListCard {
            ListRow("출결 알림", "등교·하교 시간에 알려줘요", divider = true, onClick = { flip("att", state.notifyAttendance) }, trailing = { AppSwitch(state.notifyAttendance) })
            ListRow("급식 알림", "오늘의 급식을 알려줘요", divider = true, onClick = { flip("meal", state.notifyMeal) }, trailing = { AppSwitch(state.notifyMeal) })
            ListRow("공지 알림", "새 공지가 올라오면 알려줘요", onClick = { flip("notice", state.notifyNotice) }, trailing = { AppSwitch(state.notifyNotice) })
        }
        Spacer(Modifier.height(20.dp))
        ListCard {
            ListRow("학생증 화면 밝기 높이기", "학생증을 볼 때 밝게 표시해요", onClick = { flip("bright", state.brightBoost) }, trailing = { AppSwitch(state.brightBoost) })
        }
        Spacer(Modifier.height(20.dp))
        ListCard {
            ListRow("학생증 (다시) 등록", divider = true, onClick = onReRegister)
            ListRow("등록된 학생증 삭제", onClick = onUnregister)
        }
    }
}

@Composable
fun TipsPage(onBack: () -> Unit) {
    PageFrame("유용한 정보", onBack) {
        SectionLabel("학생증 사용 방법")
        ListCard {
            ListRow("학생증 등록", "홈의 “학생증 스캔하기”로 종이 학생증을 비추면 이름과 바코드를 읽어요. 이어서 학생증 앞면 사진과 내 얼굴을 확인해요.", divider = true, descAccent = false)
            ListRow("학생증 보기", "하단 “학생증” 탭에서 바코드가 있는 모바일 학생증을 볼 수 있어요. 프로필 사진을 누르면 크게 볼 수 있어요.", divider = true, descAccent = false)
            ListRow("다시 등록", "메뉴의 “학생증 (다시) 등록”에서 언제든 다시 스캔할 수 있어요.", descAccent = false)
        }
    }
}

/** 정보 페이지: 제목 34/700 (y=109), 버전 13 (y=166), 최신 상태 13 (y=184), 오픈소스 라이선스 버튼은 아래에서 24 위 200x52 */
@Composable
fun InfoPage(onBack: () -> Unit, onLicenses: () -> Unit, onPrivacy: () -> Unit = {}) {
    val c = LocalColors.current
    val bottom = LocalBottomInset.current
    Box(Modifier.fillMaxSize()) {
        // 머리글 아래 좌표계가 아니라 페이지 전체 좌표(원본과 같은 값)로 놓는다
        PT("학생증", 196.5f, 109f, 34, if (c.dark) c.textBody else Color(0xFF252525), 700, a = A.C)
        PT("버전 1.0.0.0.23", 196.5f, 166.5f, 13, if (c.dark) c.textMuted else Color(0xFF909090), 400, a = A.C)
        PT("최신버전입니다.", 196.5f, 184f, 13, c.textSub, 400, a = A.C)
        // 버튼 둘: 오픈소스 라이선스(아래에서 24), 그 위에 개인정보처리방침(12 간격)
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp + 52.dp + 22.dp + bottom).width(200.dp).height(52.dp)) {
            PillButton("개인정보처리방침", onClick = onPrivacy, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp + bottom).width(200.dp).height(52.dp)) {
            PillButton("오픈소스 라이선스", onClick = onLicenses, modifier = Modifier.fillMaxSize())
        }
        BackHeader("정보", onBack)
    }
}

@Composable
fun LicensesPage(onBack: () -> Unit) {
    val c = LocalColors.current
    PageFrame("오픈소스 라이선스", onBack) {
        Box(Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, bottom = 16.dp)) {
            T(LegalTexts.LICENSES_INTRO, 14, c.textSub, 400, lh = 20)
        }
        SectionLabel("Android technologies")
        ListCard {
            LegalTexts.LIBS.forEachIndexed { i, lib ->
                DocRow(
                    title = lib.name,
                    lines = listOf(lib.license, lib.copyright, lib.use),
                    accentFirst = true,
                    divider = i < LegalTexts.LIBS.size - 1,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        SectionLabel("라이선스 고지")
        ListCard {
            DocRow(LegalTexts.APACHE_TITLE, listOf(LegalTexts.APACHE), divider = true)
            DocRow(LegalTexts.MIT_TITLE, listOf(LegalTexts.MIT), divider = true)
            DocRow(LegalTexts.MLKIT_TITLE, listOf(LegalTexts.MLKIT))
        }
    }
}

@Composable
fun PrivacyPage(onBack: () -> Unit) {
    val c = LocalColors.current
    PageFrame(LegalTexts.PRIVACY_TITLE, onBack) {
        Box(Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, bottom = 16.dp)) {
            T(LegalTexts.PRIVACY_INTRO, 14, c.textSub, 400, lh = 20)
        }
        ListCard {
            LegalTexts.PRIVACY.forEachIndexed { i, (h, body) ->
                DocRow(h, listOf(body), divider = i < LegalTexts.PRIVACY.size - 1)
            }
        }
    }
}

/** 긴 글 행: 제목 17/23 아래에 12~14 크기 설명 문단들 (목록 행과 같은 좌우 여백, 아래 구분선) */
@Composable
private fun DocRow(title: String, lines: List<String>, accentFirst: Boolean = false, divider: Boolean = false) {
    val c = LocalColors.current
    PressBox(Modifier.fillMaxWidth(), innerScale = 0.992f, rippleLight = 0.1f, rippleDark = 0.16f, onClick = {}) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 13.dp)) {
            T(title, 17, c.text, 400, lh = 23)
            Spacer(Modifier.height(4.dp))
            lines.forEachIndexed { i, l ->
                if (i > 0) Spacer(Modifier.height(2.dp))
                if (accentFirst && i == 0) T(l, 12, c.accentText, 400, lh = 16)
                else T(l, 13, c.textSub, 400, lh = 19)
            }
        }
        if (divider) Box(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(c.line))
    }
}

/** 구역 제목: 13/600, x=28, 카드는 그 아래 25.5 에서 시작 (원본 y=68 → 93.5) */
@Composable
private fun SectionLabel(text: String) {
    val c = LocalColors.current
    Box(Modifier.fillMaxWidth().height(25.5.dp)) {
        PT(text, 28f, 0f, 13, if (c.dark) c.textSub else Color(0xFF848487), 600)
    }
}
