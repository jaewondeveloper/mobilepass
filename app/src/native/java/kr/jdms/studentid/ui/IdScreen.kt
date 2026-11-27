package kr.jdms.studentid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 화면 캡처 테스트에서 끝나지 않는 1초 갱신 루프가 idle 판정을 막지 않도록 끌 수 있게 하는 스위치 (실제 앱에서는 항상 true) */
object IdTabConfig { @JvmField var liveClock: Boolean = true }

private val InkDark = Color(0xFF010102)
private val InkMuted = Color(0x73010102)

@Composable
fun IdTab(state: AppState, onScan: () -> Unit, onPhoto: () -> Unit, onTitle: () -> Unit = {}) {
    val c = LocalColors.current
    val sc = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().scrollEdgeFade(sc).verticalScroll(sc)) {
            Box(Modifier.fillMaxWidth().height(660.dp)) {
                PT("모바일 학생증", 22f, 86f, 14, c.pageLabel, 500)

                if (!state.registered) {
                    PutFull(115f, 147f, modifier = Modifier.glass(c)) {
                        PT("학생증을 등록해 주세요", 19f, 13f, 16, c.textBody, 600)
                        PT("종이 학생증을 스캔하면 모바일 학생증이 만들어져요.", 18f, 48f, 14, c.body2)
                        PillButton(
                            "학생증 스캔하기", onClick = onScan,
                            modifier = Modifier.align(Alignment.TopStart).offset(16.dp, 93.dp),
                            height = 38.dp, bg = c.btnBg, border = c.btnBorder, fontSize = 13, icon = kr.jdms.studentid.R.drawable.ic_scan, scale = 0.975f,
                        )
                    }
                    return@Box
                }

                // 학생증 카드: (10,115) 373x229, 둥근 28. 카드 면은 다크모드에서도 흰색 (바코드를 스캐너가 읽어야 하므로)
                PutFull(115f, 229f, modifier = Modifier.clip(CardShape).background(Color(0xFFFCFCFF))) {
                    PT("중동중학교", 16f, 16f, 12, InkDark, 600, lh = 16)
                    PT("모바일 학생증", 16f, 32f, 12, InkDark, 600, lh = 16)
                    PT(state.name ?: "", 16f, 48f, 12, InkMuted, 600, lh = 16)
                    AvatarBox(state, Modifier.align(Alignment.TopEnd).offset((-10).dp, 12.dp), onClick = onPhoto)       // 사진을 누르면 확대
                    PT(state.code ?: "", 196.5f, 86f, 16, InkDark, 600, a = A.C, align = TextAlign.Center, ls = 0.5f)
                    Box(Modifier.align(Alignment.TopCenter).offset(y = 120.dp).width(202.dp).height(64.dp).background(Color.White)) {
                        Code128(state.code ?: "", 64.dp, Modifier.width(202.dp))
                    }
                    Clock(Modifier.align(Alignment.TopCenter).offset(y = 196.dp))
                }

                // 다시 등록하기: (96.5,360) 200x52
                Put(96.5f, 360f, 200f, 52f, A.C) {
                    PillButton("학생증 다시 등록하기", onClick = onScan, modifier = Modifier.fillMaxSize())
                }
            }
        }
        SchoolTitle(onTitle, glass = sc.value > 0)
    }
}

@Composable
private fun Clock(modifier: Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { if (IdTabConfig.liveClock) while (true) { now = Date(); delay(1000) } }
    val fmt = remember { SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.KOREA) }
    T(fmt.format(now), 12, InkMuted, 400, align = TextAlign.Center, softWrap = false, single = true, modifier = modifier)
}
