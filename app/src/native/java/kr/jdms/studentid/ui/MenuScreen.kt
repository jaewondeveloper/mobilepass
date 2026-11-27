package kr.jdms.studentid.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.jdms.studentid.R

@Composable
fun MenuTab(onReRegister: () -> Unit, onTips: () -> Unit, onInfo: () -> Unit, onSettings: () -> Unit) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 96.dp)) {
        Box(Modifier.fillMaxWidth().height(68.dp).padding(start = 24.dp, end = 16.dp)) {
            Text("중동중학교", color = c.text, fontSize = 26.fsp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterStart))
            Box(Modifier.align(Alignment.CenterEnd).size(40.dp, 48.dp).clickable(onClick = onSettings), contentAlignment = Alignment.Center) {
                Ico(R.drawable.ic_settings, 24.dp, tint = c.text)
            }
        }
        ImageCard("학생증 (다시) 등록", "학생증을 스캔해서 등록해요.", R.drawable.img_menu_flag, onReRegister)
        Spacer(Modifier.height(20.dp))
        ImageCard("유용한 정보", "학생증 사용법과 학교 생활 기능을 알아 보세요.", R.drawable.img_menu_ufo, onTips)
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(c.card).clickable(onClick = onInfo).padding(start = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ico(R.drawable.ic_info, 24.dp, tint = c.text)
            Spacer(Modifier.width(18.dp))
            Text("정보", color = c.text, fontSize = 17.fsp)
        }
    }
}

@Composable
private fun ImageCard(title: String, desc: String, image: Int, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(116.dp).clip(CardShape).background(c.card).clickable(onClick = onClick).padding(start = 18.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = c.text, fontSize = 20.fsp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(desc, color = c.textBody, fontSize = 15.fsp)
        }
        Image(painterResource(image), null, Modifier.width(82.dp))
    }
}
