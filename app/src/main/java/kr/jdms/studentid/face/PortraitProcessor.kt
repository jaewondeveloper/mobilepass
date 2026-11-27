package kr.jdms.studentid.face

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.io.ByteArrayOutputStream
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 셀피 한 장 → 학생증 프로필 사진(PNG, 배경 투명).
 *
 *  1. 각도 보정: 두 눈이 수평이 되도록 눈 중점을 기준으로 회전 (좌우 기울기). 고개를 좌우/상하로 돌린 것은
 *     사진 한 장으로 바로잡을 수 없으므로, 촬영 단계에서 정면일 때만 찍도록 걸러낸다.
 *  2. 구도 보정: 눈 간격을 기준으로 머리~어깨가 일정한 비율(3:4)로 들어오게 자름 (눈은 위에서 40% 지점)
 *  3. 배경 제거: ML Kit 셀피 세그멘테이션 마스크 → 가장자리를 부드럽게 한 알파
 *  4. 약한 보정: 인물 영역 밝기/대비 자동 보정 + 아주 약한 피부 매끄럽게 (본인 식별을 해치지 않는 수준)
 */
class PortraitProcessor {
    private val segmenter by lazy {
        Segmentation.getClient(
            SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                .enableRawSizeMask()
                .build(),
        )
    }

    /** 백그라운드 스레드에서 호출 (ML Kit 결과를 기다린다). 실패하면 null */
    fun create(frame: Bitmap, face: FaceInfo): ByteArray? {
        var rotated: Bitmap? = null
        try {
            // 1) 각도 보정
            val mid = Pt((face.eyeL.x + face.eyeR.x) / 2f, (face.eyeL.y + face.eyeR.y) / 2f)
            val roll = FaceMath.rollDegrees(face.eyeL, face.eyeR)
            rotated = rotateAround(frame, -roll, mid)

            // 2) 구도: 눈 간격 기준
            val eyeDist = hypot((face.eyeR.x - face.eyeL.x).toDouble(), (face.eyeR.y - face.eyeL.y).toDouble()).toFloat()
            var cw = eyeDist * 4.2f
            var ch = cw * 4f / 3f
            val fit = min(1f, min(rotated.width / cw, rotated.height / ch))
            cw *= fit
            ch *= fit
            val left = (mid.x - cw / 2f).coerceIn(0f, rotated.width - cw)
            val top = (mid.y - 0.40f * ch).coerceIn(0f, rotated.height - ch)

            // 3) 배경 제거용 마스크 (회전된 이미지 기준)
            val seg = Tasks.await(segmenter.process(InputImage.fromBitmap(rotated, 0)))
            val mw = seg.width
            val mh = seg.height
            val mask = FloatArray(mw * mh)
            seg.buffer.rewind()
            seg.buffer.asFloatBuffer().get(mask)

            // 출력 격자 (480x640): 자른 영역을 줄여 색을 가져오고, 마스크는 같은 위치를 최근접으로 가져온다
            val ow = OUT_W
            val oh = OUT_H
            val crop = Bitmap.createBitmap(rotated, left.roundToInt(), top.roundToInt(), cw.roundToInt().coerceAtLeast(2), ch.roundToInt().coerceAtLeast(2))
            val scaled = Bitmap.createScaledBitmap(crop, ow, oh, true)
            if (crop !== rotated) crop.recycle()
            val px = IntArray(ow * oh)
            scaled.getPixels(px, 0, ow, 0, 0, ow, oh)
            scaled.recycle()

            val alpha = FloatArray(ow * oh)
            val sx = cw / ow
            val sy = ch / oh
            for (y in 0 until oh) {
                val my = ((top + (y + 0.5f) * sy) * mh / rotated.height).toInt().coerceIn(0, mh - 1)
                for (x in 0 until ow) {
                    val mx = ((left + (x + 0.5f) * sx) * mw / rotated.width).toInt().coerceIn(0, mw - 1)
                    alpha[y * ow + x] = smoothstep(0.25f, 0.8f, mask[my * mw + mx])
                }
            }
            softenAlpha(alpha, ow, oh)

            // 4) 약한 보정
            retouch(px, alpha, ow, oh)

            val out = IntArray(ow * oh)
            for (i in out.indices) {
                val a = (alpha[i] * 255f).roundToInt().coerceIn(0, 255)
                out[i] = (a shl 24) or (px[i] and 0x00FFFFFF)
            }
            val bmp = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
            bmp.setPixels(out, 0, ow, 0, 0, ow, oh)
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
            bmp.recycle()
            return bos.toByteArray()
        } catch (t: Throwable) {
            Log.w(TAG, "portrait", t)
            return null
        } finally {
            if (rotated != null && rotated !== frame) rotated.recycle()
        }
    }

    fun close() = runCatching { segmenter.close() }

    private fun rotateAround(src: Bitmap, degrees: Float, c: Pt): Bitmap {
        if (kotlin.math.abs(degrees) < 0.3f) return src.copy(Bitmap.Config.ARGB_8888, false)
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val m = Matrix().apply { postRotate(degrees, c.x, c.y) }
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** 알파 가장자리를 부드럽게 (톱니/후광 줄이기) */
    private fun softenAlpha(a: FloatArray, w: Int, h: Int) {
        repeat(2) {
            val src = a.copyOf()
            for (y in 1 until h - 1) for (x in 1 until w - 1) {
                var s = 0f
                for (dy in -1..1) for (dx in -1..1) s += src[(y + dy) * w + x + dx]
                a[y * w + x] = s / 9f
            }
        }
    }

    private fun retouch(px: IntArray, alpha: FloatArray, w: Int, h: Int) {
        // 인물 영역의 밝기 분포로 2~98 퍼센타일을 구해 60% 강도로 대비/밝기 맞춤
        val hist = IntArray(256)
        var cnt = 0
        for (i in px.indices) if (alpha[i] > 0.5f) {
            val p = px[i]
            hist[(0.299 * ((p shr 16) and 0xFF) + 0.587 * ((p shr 8) and 0xFF) + 0.114 * (p and 0xFF)).toInt().coerceIn(0, 255)]++
            cnt++
        }
        if (cnt < 100) return
        fun pct(p: Double): Int { var acc = 0; val target = (p * cnt).toInt(); for (v in 0..255) { acc += hist[v]; if (acc >= target) return v }; return 255 }
        val lo = pct(0.02)
        val hi = max(pct(0.98), lo + 48)
        val gain = (255f / (hi - lo)).coerceIn(0.9f, 1.3f)
        val strength = 0.6f

        // 아주 약한 피부 매끄럽게: 흐린 본 + 원본 18% 섞기 (눈/입 디테일이 죽지 않도록 약하게)
        val r = FloatArray(w * h) { ((px[it] shr 16) and 0xFF).toFloat() }
        val g = FloatArray(w * h) { ((px[it] shr 8) and 0xFF).toFloat() }
        val b = FloatArray(w * h) { (px[it] and 0xFF).toFloat() }
        val rb = boxBlur(r, w, h, 2)
        val gb = boxBlur(g, w, h, 2)
        val bb = boxBlur(b, w, h, 2)
        val smooth = 0.18f
        for (i in px.indices) {
            val chans = floatArrayOf(r[i], g[i], b[i])
            val blur = floatArrayOf(rb[i], gb[i], bb[i])
            var out = 0
            for (c in 0..2) {
                val mixed = chans[c] * (1 - smooth) + blur[c] * smooth
                val leveled = (mixed - lo) * gain
                val v = (mixed * (1 - strength) + leveled * strength).roundToInt().coerceIn(0, 255)
                out = (out shl 8) or v
            }
            px[i] = 0xFF000000.toInt() or out
        }
    }

    /** 분리형 박스 블러 */
    private fun boxBlur(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        val tmp = FloatArray(src.size)
        val out = FloatArray(src.size)
        val k = 2 * radius + 1
        for (y in 0 until h) {
            var s = 0f
            for (x in -radius..radius) s += src[y * w + x.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[y * w + x] = s / k
                s += src[y * w + (x + radius + 1).coerceIn(0, w - 1)] - src[y * w + (x - radius).coerceIn(0, w - 1)]
            }
        }
        for (x in 0 until w) {
            var s = 0f
            for (y in -radius..radius) s += tmp[y.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = s / k
                s += tmp[(y + radius + 1).coerceIn(0, h - 1) * w + x] - tmp[(y - radius).coerceIn(0, h - 1) * w + x]
            }
        }
        return out
    }

    private companion object {
        const val TAG = "PortraitProcessor"
        const val OUT_W = 480
        const val OUT_H = 640
    }
}
