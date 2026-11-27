package kr.jdms.studentid.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kr.jdms.studentid.R

@Composable
fun MenuTab(onReRegister: () -> Unit, onTips: () -> Unit, onInfo: () -> Unit, onSettings: () -> Unit) {
    val c = LocalColors.current
    val sc = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().scrollEdgeFade(sc).verticalScroll(sc)) {
        Box(Modifier.fillMaxWidth().height(660.dp)) {

            ImageCard(68f, "학생증 (다시) 등록", "학생증을 스캔해서 등록해요.", R.drawable.img_menu_flag, onReRegister)
            ImageCard(204f, "유용한 정보", "학생증 사용법과 학교 생활 기능을 알아 보세요.", R.drawable.img_menu_ufo, onTips)

            // 정보 행: (10,340) 373x52, 둥근 24. 행 물결(0.1) 위에 카드 전체 물결(0.06)이 한 겹 더 깔리고, 내용은 0.992 로 줄어든다.
            PutFull(340f, 52f) {
                PressBox(
                    Modifier.fillMaxSize(), shape = RoundedCornerShape(24.dp), innerScale = 0.992f, innerFill = true,
                    rippleLight = 0.1f, rippleDark = 0.16f, extraLight = 0.06f, extraDark = 0.12f, bg = c.card, onClick = onInfo,
                ) {
                    Ico(R.drawable.ic_info, 28.dp, tint = c.text, modifier = Modifier.align(Alignment.TopStart).offset(18.dp, 12.dp))
                    PT("정보", 62f, 15f, 17, c.text, 400, lh = 23)
                }
            }
        }
        }
        // 머리글(제목 26/700 x=24, 설정 버튼은 오른쪽에서 12 떨어진 48x48 원)은 스크롤 밖에 떠 있고, 스크롤되면 버튼 뒤에 유리 배경이 생긴다
        val titleAlpha by androidx.compose.animation.core.animateFloatAsState(if (sc.value > 0) 0f else 1f, androidx.compose.animation.core.tween(145, easing = androidx.compose.animation.core.LinearEasing), label = "titleFade")
        PT("중동중학교", 24f, 20.5f, 26, c.text, 700, modifier = Modifier.graphicsLayer { alpha = titleAlpha })
        ActionIcon(R.drawable.ic_settings, Modifier.align(Alignment.TopEnd).offset((-12).dp, 12.dp), onSettings, glass = sc.value > 0)
    }
}

/** 메뉴 카드: 373x116, 둥근 24. 글자는 (18,34)/(18,64), 그림은 오른쪽에서 16, 위에서 10 의 104x96. 누르면 물결만(0.06) 깔린다. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ImageCard(y: Float, title: String, desc: String, image: Int, onClick: () -> Unit) {
    val c = LocalColors.current
    PutFull(y, 116f) {
        PressBox(Modifier.fillMaxSize(), shape = RoundedCornerShape(24.dp), rippleLight = 0.06f, rippleDark = 0.12f, bg = c.card, onClick = onClick) {
            // 제목+설명 묶음은 카드 세로 가운데 (설명이 두 줄이면 묶음이 위로 올라감)
            androidx.compose.foundation.layout.Column(Modifier.align(Alignment.CenterStart).padding(start = 18.dp)) {
                T(title, 20, c.text, 600, single = true)
                androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
                T(desc, 15, c.menuDesc, 400, lh = 19, modifier = Modifier.width(235.dp))
            }
            Image(
                painterResource(image), null,
                Modifier.align(Alignment.TopEnd).offset((-16).dp, 10.dp).size(104.dp, 96.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
