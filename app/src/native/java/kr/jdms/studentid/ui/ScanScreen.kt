package kr.jdms.studentid.ui

import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay

private val OvalShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path().apply { addOval(Rect(0f, 0f, size.width, size.height)) })
}

/**
 * 스캔 페이지. 웹 버전과 같은 구성: 뒤로가기 머리글 + 안내 문구 + 틀. 틀 바깥은 페이지 배경 그대로이고
 * 카메라는 틀 안쪽에만 보인다. (글자/앞면 단계는 세로 카드 모양, 얼굴 단계는 타원)
 */
@Composable
fun ScanScreen(scan: ScanController, onBack: () -> Unit) {
    val c = LocalColors.current
    val cfg = LocalConfiguration.current
    val oval = scan.step == Step.SELFIE
    val w: Dp = if (oval) minOf(250.dp, (cfg.screenWidthDp * 0.68f).dp) else minOf(270.dp, (cfg.screenWidthDp * 0.72f).dp)
    val h: Dp = if (oval) minOf(330.dp, (cfg.screenHeightDp * 0.46f).dp) else minOf(420.dp, (cfg.screenHeightDp * 0.62f).dp)

    // 단계가 바뀌거나 다시 시작할 때: 페이지 전환/틀 모양 변경이 끝난 뒤에 카메라를 켠다
    LaunchedEffect(scan.step, scan.startTick) {
        delay(350)
        scan.startCamera()
    }
    DisposableEffect(Unit) { onDispose { scan.end() } }

    Column(Modifier.fillMaxSize()) {
        BackHeader(scan.title, onBack)
        Text(
            scan.hint, color = c.textSub, fontSize = 15.fsp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp).padding(start = 16.dp, end = 16.dp, top = 4.dp),
        )
        Box(Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.TopCenter) {
            val shape: Shape = if (oval) OvalShape else androidx.compose.foundation.shape.RoundedCornerShape(19.dp)
            val borderColor = if (scan.detected) androidx.compose.ui.graphics.Color(0xFF0088FF) else c.textSub
            Box(
                Modifier.size(w, h).drawBehind {
                    val stroke = 3.dp.toPx()
                    val inset = stroke / 2f
                    val rect = Rect(inset, inset, size.width - inset, size.height - inset)
                    val style = if (scan.detected) Stroke(stroke) else Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())))
                    if (oval) drawOval(borderColor, rect.topLeft, rect.size, style = style)
                    else drawRoundRect(borderColor, rect.topLeft, rect.size, CornerRadius(22.dp.toPx() - inset), style = style)
                }.padding(3.dp),
            ) {
                if (scan.previewVisible) {
                    AndroidView(
                        factory = {
                            scan.previewView.also { (it.parent as? ViewGroup)?.removeView(it) }
                        },
                        modifier = Modifier.fillMaxSize().clip(shape),
                        onRelease = { (it.parent as? ViewGroup)?.removeView(it) },
                    )
                }
            }
        }
    }
}
