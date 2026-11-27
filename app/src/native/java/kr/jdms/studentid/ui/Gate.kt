package kr.jdms.studentid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 학생증을 등록하기 전에 전체를 가리는 시작 화면: 가운데 "학생증", 맨 아래 파란 "시작하기" 버튼(템플릿의 PillButton 재사용,
 * 색만 강조색/흰 글자). 오른쪽 위 모서리를 연속 7번 누르면 데모 모드로 들어간다(설정에서 '데모 종료'로 빠져나올 수 있음).
 */
@Composable
fun StartGate(onStart: () -> Unit, onDemo: () -> Unit) {
    val c = LocalColors.current
    val bottom = LocalBottomInset.current
    var taps by remember { mutableStateOf(0) }
    var resetJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(c.pageBg)) {
        T("학생증", 32, c.text, 700, modifier = Modifier.align(Alignment.Center))

        // 오른쪽 위 숨은 데모 진입 자리 (1초 안에 7번)
        Box(
            Modifier.align(Alignment.TopEnd).size(84.dp).pointerInput(Unit) {
                detectTapGestures {
                    taps++
                    resetJob?.cancel()
                    resetJob = scope.launch { delay(1000); taps = 0 }
                    if (taps >= 7) { taps = 0; resetJob?.cancel(); onDemo() }
                }
            },
        )

        PillButton(
            "시작하기", onClick = onStart,
            modifier = Modifier.align(Alignment.BottomCenter).width(196.dp).padding(bottom = 24.dp + bottom),
            height = 48.dp, bg = c.accent, fg = Color(0xFFFCFCFF), fontSize = 16,
        )
    }
}
