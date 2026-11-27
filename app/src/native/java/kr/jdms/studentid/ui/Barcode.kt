package kr.jdms.studentid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.oned.Code128Writer

/** Code128 바코드 (웹 버전의 JsBarcode 와 같은 형식). 모듈 폭을 정수 픽셀로 맞춰 스캐너가 읽기 좋게 그린다. */
@Composable
fun Code128(value: String, height: Dp, modifier: Modifier = Modifier, color: Color = Color.Black) {
    val matrix = remember(value) { runCatching { Code128Writer().encode(value, BarcodeFormat.CODE_128, 0, 1) }.getOrNull() }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val m = matrix ?: return@Canvas
        val modules = m.width
        val unit = kotlin.math.floor(size.width / modules).coerceAtLeast(1f)          // 모듈 하나의 픽셀 폭 (정수)
        val total = unit * modules
        val left = (size.width - total) / 2f
        var x = 0
        while (x < modules) {
            if (m.get(x, 0)) {
                var run = 1
                while (x + run < modules && m.get(x + run, 0)) run++
                drawRect(color, Offset(left + x * unit, 0f), Size(run * unit, size.height))
                x += run
            } else x++
        }
    }
}
