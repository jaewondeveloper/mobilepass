package kr.jdms.studentid.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 원본 HTML(393 x 852 기준)에서 측정한 좌표를 그대로 쓰기 위한 배치 도구.
 * 왼쪽 기준(L)은 왼쪽에서, 오른쪽 기준(R)은 오른쪽에서, 가운데 기준(C)은 가운데에서 같은 간격을 유지해
 * 화면 폭이 달라도 원본처럼 늘어나거나 붙는다.
 */
const val DESIGN_W = 393f

enum class A { L, C, R }

/** 원본의 (x, y, w, h) 자리에 상자를 놓는다. C 는 x 가 '상자의 왼쪽'이고 가운데 기준 간격이 유지된다. */
@Composable
fun BoxScope.Put(x: Float, y: Float, w: Float, h: Float, a: A = A.L, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val base = when (a) {
        A.L -> Modifier.align(Alignment.TopStart).offset(x.dp, y.dp)
        A.C -> Modifier.align(Alignment.TopCenter).offset((x + w / 2f - DESIGN_W / 2f).dp, y.dp)
        A.R -> Modifier.align(Alignment.TopEnd).offset((-(DESIGN_W - x - w)).dp, y.dp)
    }
    Box(base.size(w.dp, h.dp).then(modifier), content = content)
}

/** 좌우 여백(기본 10)만 두고 폭을 채우는 카드 자리 */
@Composable
fun BoxScope.PutFull(y: Float, h: Float, margin: Float = 10f, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.align(Alignment.TopStart).offset(y = y.dp).padding(horizontal = margin.dp).fillMaxWidth().height(h.dp).then(modifier),
        content = content,
    )
}

/** 원본 글꼴 크기별 'line-height: normal' 높이(측정값). 글자 상자 높이를 원본과 같게 맞춘다. */
fun normalLh(fs: Int): Int = when (fs) {
    10 -> 12; 12 -> 15; 13 -> 16; 14 -> 17; 15 -> 18; 16 -> 20; 17 -> 20; 18 -> 21; 20 -> 24; 26 -> 31; 34 -> 41
    else -> (fs * 1.2f).toInt()
}

/** 원본과 같은 글자 크기·굵기·줄 높이의 글자 */
@Composable
fun T(
    text: String, fs: Int, color: Color, weight: Int = 400, lh: Int? = null,
    align: TextAlign = TextAlign.Start, ls: Float = 0f, maxLines: Int = Int.MAX_VALUE, softWrap: Boolean = true,
    single: Boolean = false, modifier: Modifier = Modifier,
) {
    val l = lh ?: normalLh(fs)
    // 한 줄 글자는 기기 글꼴의 높이와 상관없이 글자 상자 높이를 원본의 줄 높이(l)에 딱 맞춘다 (글자는 그 안에서 가운데)
    val m = if (single) modifier.height(l.dp).wrapContentHeight(Alignment.CenterVertically, unbounded = true) else modifier
    Text(
        text, color = color, fontSize = fs.fsp, lineHeight = l.fsp,
        fontWeight = FontWeight(weight), textAlign = align, letterSpacing = ls.fsp0,
        maxLines = maxLines, softWrap = softWrap, modifier = m,
        // 한글은 원본(word-break: keep-all)처럼 낱말(띄어쓰기) 단위로만 줄을 바꾼다
        style = androidx.compose.material3.LocalTextStyle.current.merge(
            androidx.compose.ui.text.TextStyle(
                lineBreak = androidx.compose.ui.text.style.LineBreak(
                    androidx.compose.ui.text.style.LineBreak.Strategy.Simple,
                    androidx.compose.ui.text.style.LineBreak.Strictness.Normal,
                    androidx.compose.ui.text.style.LineBreak.WordBreak.Phrase,
                ),
            ),
        ),
    )
}

val Float.fsp0 get() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)

/**
 * 원본에서 잰 글자 위치(y = 글자 영역 윗변)에 글자를 놓는다.
 * lh 를 따로 준 글자(원본 line-height 지정)는 줄 상자가 글자 영역보다 높으므로 그만큼 위로 올린다.
 */
@Composable
fun BoxScope.PT(
    text: String, x: Float, y: Float, fs: Int, color: Color, weight: Int = 400, lh: Int? = null,
    a: A = A.L, w: Float? = null, align: TextAlign = TextAlign.Start, ls: Float = 0f,
    maxLines: Int = Int.MAX_VALUE, modifier: Modifier = Modifier,
) {
    val top = y - ((lh ?: normalLh(fs)) - normalLh(fs)) / 2f
    val base = when (a) {
        // C: x 는 글자의 가운데 좌표
        A.C -> Modifier.align(Alignment.TopCenter).offset((x - DESIGN_W / 2f).dp, top.dp)
        A.R -> Modifier.align(Alignment.TopEnd).offset((-(DESIGN_W - x)).dp, top.dp)
        A.L -> Modifier.align(Alignment.TopStart).offset(x.dp, top.dp)
    }
    val sizedMod = if (w != null) base.width(w.dp) else base
    T(text, fs, color, weight, lh, align, ls, maxLines, softWrap = w != null, single = w == null, modifier = sizedMod.then(modifier))
}

/** 시스템 하단바 높이(화면 맨 아래에 붙는 요소가 읽는다) */
val LocalBottomInset = androidx.compose.runtime.compositionLocalOf { 0.dp }
