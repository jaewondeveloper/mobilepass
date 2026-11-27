package kr.jdms.studentid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun IdTab(state: AppState, onScan: () -> Unit, onPhoto: () -> Unit) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 96.dp)) {
        TitleBar()
        Text("모바일 학생증", color = c.textMuted, fontSize = 14.fsp, modifier = Modifier.padding(start = 22.dp, top = 6.dp, bottom = 10.dp))

        if (!state.registered) {
            Column(Modifier.padding(horizontal = 10.dp).fillMaxWidth().glass(c).padding(start = 19.dp, top = 16.dp, end = 19.dp, bottom = 20.dp)) {
                Text("학생증을 등록해 주세요", color = c.textBody, fontSize = 16.fsp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(13.dp))
                Text("종이 학생증을 스캔하면 모바일 학생증이 만들어져요.", color = c.textBody.copy(alpha = 0.85f), fontSize = 14.fsp)
                Spacer(Modifier.height(24.dp))
                PillButton("학생증 스캔하기", onClick = onScan, height = 38.dp, bg = c.btnBg, border = c.btnBorder, fontSize = 13, icon = kr.jdms.studentid.R.drawable.ic_scan)
            }
            return@Column
        }

        // 학생증 카드
        Column(Modifier.padding(horizontal = 10.dp).fillMaxWidth().clip(CardShape).background(c.card)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 12.dp)) {
                Column(Modifier.weight(1f).padding(top = 4.dp)) {
                    Text("중동중학교", color = Color(0xFF010102), fontSize = 12.fsp, fontWeight = FontWeight.SemiBold)
                    Text("모바일 학생증", color = Color(0xFF010102), fontSize = 12.fsp, fontWeight = FontWeight.SemiBold)
                    Text(state.name ?: "", color = Color(0x73010102), fontSize = 12.fsp, fontWeight = FontWeight.SemiBold)
                }
                AvatarBox(state, Modifier, onClick = onPhoto)       // 사진을 누르면 확대
            }
            Spacer(Modifier.height(28.dp))
            // 카드 면은 다크모드에서도 흰색 (바코드를 스캐너가 읽어야 하므로)
            Text(state.code ?: "", color = Color(0xFF010102), fontSize = 16.fsp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5f.fsp0, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(Color.White).padding(vertical = 0.dp)) {
                Code128(state.code ?: "", 64.dp)
            }
            Spacer(Modifier.height(12.dp))
            Clock()
            Spacer(Modifier.height(18.dp))
        }

        Spacer(Modifier.height(17.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PillButton("학생증 다시 등록하기", onClick = onScan, modifier = Modifier.width(200.dp))
        }
    }
}

private val Float.fsp0 get() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)

@Composable
private fun Clock() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }
    val fmt = remember { SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.KOREA) }
    Text(fmt.format(now), color = Color(0x73010102), fontSize = 12.fsp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}
