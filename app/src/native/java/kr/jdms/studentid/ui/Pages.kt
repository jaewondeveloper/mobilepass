package kr.jdms.studentid.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 뒤로가기 머리글(높이 68) 아래에 내용이 이어지는 페이지 틀 */
@Composable
private fun PageFrame(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        BackHeader(title, onBack)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) { content() }
    }
}

@Composable
fun SettingsPage(state: AppState, onBack: () -> Unit, onReRegister: () -> Unit, onUnregister: () -> Unit) {
    PageFrame("설정", onBack) {
        ListCard {
            ListRow("출결 알림", "등교·하교 시간에 알려줘요", divider = true, onClick = { state.setToggle("att", !state.notifyAttendance) }, trailing = { AppSwitch(state.notifyAttendance) })
            ListRow("급식 알림", "오늘의 급식을 알려줘요", divider = true, onClick = { state.setToggle("meal", !state.notifyMeal) }, trailing = { AppSwitch(state.notifyMeal) })
            ListRow("공지 알림", "새 공지가 올라오면 알려줘요", onClick = { state.setToggle("notice", !state.notifyNotice) }, trailing = { AppSwitch(state.notifyNotice) })
        }
        Spacer(Modifier.height(20.dp))
        ListCard {
            ListRow("학생증 화면 밝기 높이기", "학생증을 볼 때 밝게 표시해요", onClick = { state.setToggle("bright", !state.brightBoost) }, trailing = { AppSwitch(state.brightBoost) })
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
fun InfoPage(onBack: () -> Unit, onLicenses: () -> Unit) {
    val c = LocalColors.current
    val bottom = LocalBottomInset.current
    Box(Modifier.fillMaxSize()) {
        // 머리글 아래 좌표계가 아니라 페이지 전체 좌표(원본과 같은 값)로 놓는다
        PT("학생증", 196.5f, 109f, 34, if (c.dark) c.textBody else Color(0xFF252525), 700, a = A.C)
        PT("버전 1.0.0.0.23", 196.5f, 166.5f, 13, if (c.dark) c.textMuted else Color(0xFF909090), 400, a = A.C)
        PT("최신버전입니다.", 196.5f, 184f, 13, c.textSub, 400, a = A.C)
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp + bottom).width(200.dp).height(52.dp)) {
            PillButton("오픈소스 라이선스", onClick = onLicenses, modifier = Modifier.fillMaxSize())
        }
        BackHeader("정보", onBack)
    }
}

@Composable
fun LicensesPage(onBack: () -> Unit) {
    PageFrame("오픈소스 라이선스", onBack) {
        SectionLabel("Android technologies")
        ListCard {
            val items = listOf(
                "Jetpack Compose, AndroidX" to "Apache License 2.0",
                "AndroidX CameraX" to "Apache License 2.0",
                "Google ML Kit (Barcode, Text, Face, Segmentation)" to "ML Kit Terms of Service",
                "ONNX Runtime" to "MIT License",
                "ZXing core" to "Apache License 2.0",
                "Kotlin Standard Library" to "Apache License 2.0",
            )
            items.forEachIndexed { i, (n, l) -> ListRow(n, l, divider = i < items.size - 1) }
        }
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
