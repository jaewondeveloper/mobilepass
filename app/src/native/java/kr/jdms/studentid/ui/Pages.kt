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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
            ListRow("출결 알림", "등교·하교 시간에 알려줘요", divider = true, trailing = { AppSwitch(state.notifyAttendance) { state.setToggle("att", it) } })
            ListRow("급식 알림", "오늘의 급식을 알려줘요", divider = true, trailing = { AppSwitch(state.notifyMeal) { state.setToggle("meal", it) } })
            ListRow("공지 알림", "새 공지가 올라오면 알려줘요", trailing = { AppSwitch(state.notifyNotice) { state.setToggle("notice", it) } })
        }
        Spacer(Modifier.height(20.dp))
        ListCard {
            ListRow("학생증 화면 밝기 높이기", "학생증을 볼 때 밝게 표시해요", trailing = { AppSwitch(state.brightBoost) { state.setToggle("bright", it) } })
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

@Composable
fun InfoPage(onBack: () -> Unit, onLicenses: () -> Unit) {
    val c = LocalColors.current
    PageFrame("정보", onBack) {
        Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("학생증", color = c.text, fontSize = 24.fsp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("중동중학교 모바일 학생증", color = c.textSub, fontSize = 14.fsp)
            Spacer(Modifier.height(4.dp))
            Text("버전 1.0.0", color = c.textMuted, fontSize = 13.fsp)
            Spacer(Modifier.height(28.dp))
            PillButton("오픈소스 라이선스", onClick = onLicenses, modifier = Modifier.width(220.dp))
        }
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
            items.forEachIndexed { i, (n, l) -> ListRow(n, l, divider = i < items.size - 1, descAccent = false) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val c = LocalColors.current
    Box(Modifier.fillMaxWidth().padding(start = 20.dp, bottom = 8.dp, top = 4.dp)) {
        Text(text, color = c.textSub, fontSize = 13.fsp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterStart))
    }
}
