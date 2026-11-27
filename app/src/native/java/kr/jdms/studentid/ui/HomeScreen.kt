package kr.jdms.studentid.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.jdms.studentid.R

/** 학교 이름 머리글 (모든 탭 공통, 웹 화면의 상단 바와 같은 위치) */
@Composable
fun TitleBar(size: Int = 18) {
    val c = LocalColors.current
    Box(Modifier.fillMaxWidth().height(64.dp).padding(start = 24.dp), contentAlignment = Alignment.CenterStart) {
        Text("중동중학교", color = c.text, fontSize = size.fsp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun HomeTab(state: AppState, onScan: () -> Unit, onOpenCard: () -> Unit, onSoon: () -> Unit) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 96.dp)) {
        TitleBar()
        Spacer(Modifier.height(10.dp))

        // '지금' 카드: 등록 안내 / 등록 완료
        Column(Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(146.dp).glass(c).padding(start = 19.dp, top = 13.dp, end = 19.dp)) {
            Text(
                if (state.registered) "학생증이 등록되었어요" else "학생증을 등록해 주세요",
                color = c.textBody, fontSize = 16.fsp, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(13.dp))
            Text(
                if (state.registered) "${state.name} · ${state.code}" else "종이 학생증을 스캔하면 모바일 학생증이 만들어져요.",
                color = c.textBody.copy(alpha = 0.85f), fontSize = 14.fsp,
            )
            Spacer(Modifier.height(24.dp))
            PillButton(
                if (state.registered) "학생증 보기" else "학생증 스캔하기",
                onClick = if (state.registered) onOpenCard else onScan,
                height = 38.dp, bg = c.btnBg, border = c.btnBorder, fontSize = 13, icon = R.drawable.ic_scan,
            )
        }

        Spacer(Modifier.height(53.dp))
        Text("학교생활", color = c.text.copy(alpha = 0.7f), fontSize = 13.fsp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 20.dp))
        Spacer(Modifier.height(9.dp))

        // 대시보드: 4열 타일
        Column(Modifier.padding(horizontal = 10.dp).fillMaxWidth().clip(CardShape).background(c.card).padding(start = 8.dp, top = 9.dp, end = 8.dp, bottom = 8.dp)) {
            Text("대시보드", color = c.textSub, fontSize = 13.fsp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
            val tiles = listOf(
                Triple(R.drawable.ic_attend, "출결", onSoon),
                Triple(R.drawable.ic_meal, "급식", onSoon),
                Triple(R.drawable.ic_timetable, "시간표", onSoon),
                Triple(R.drawable.ic_notice, "공지", onSoon),
                Triple(R.drawable.ic_profile, if (state.registered) "학생증 등록" else "학생증 없음", onOpenCard),
                Triple(R.drawable.ic_scan, "스캔", onScan),
            )
            tiles.chunked(4).forEachIndexed { i, row ->
                if (i > 0) Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { (icon, label, click) -> Tile(icon, label, click, Modifier.weight(1f)) }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        Spacer(Modifier.height(11.dp))

        // 학생증 한 줄
        Row(
            Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(77.dp).clip(CardShape).background(c.card).clickable(onClick = onOpenCard).padding(start = 16.dp, end = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("중동중학교", color = c.text, fontSize = 12.fsp, fontWeight = FontWeight.SemiBold)
                Text("모바일 학생증", color = c.text, fontSize = 12.fsp, fontWeight = FontWeight.SemiBold)
                Text(state.name ?: "미등록", color = c.textMuted, fontSize = 12.fsp, fontWeight = FontWeight.SemiBold)
            }
            AvatarBox(state, Modifier)
        }
    }
}

@Composable
private fun Tile(icon: Int, label: String, onClick: () -> Unit, modifier: Modifier) {
    val c = LocalColors.current
    Column(
        modifier.height(63.dp).clip(RoundedCornerShape(20.dp)).background(c.tile).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(6.dp))
        Ico(icon, 34.dp)
        Spacer(Modifier.height(2.dp))
        Text(label, color = c.textSub, fontSize = 12.fsp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** 학생증 카드의 프로필 자리: 내 사진이 있으면 사진, 없으면 기본 아이콘 */
@Composable
fun AvatarBox(state: AppState, modifier: Modifier, onClick: (() -> Unit)? = null) {
    val c = LocalColors.current
    val photo = state.photo
    Box(
        modifier.size(56.dp).clip(RoundedCornerShape(20.dp)).background(if (c.dark) Color0x14 else Color0x0D)
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

private val Color0x14 = androidx.compose.ui.graphics.Color(0x14FFFFFF)
private val Color0x0D = androidx.compose.ui.graphics.Color(0x0D000000)
