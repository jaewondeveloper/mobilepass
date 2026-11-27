package kr.jdms.studentid

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import kotlin.math.min
import kotlin.math.pow

/**
 * 앱을 처음 켤 때 보이는 시작 화면.
 *  1) 로고가 가운데에서 부드럽고 빠르게 커지며 나타나고 (0.42초, 감속)
 *  2) 로고 모양 안쪽으로만 비스듬한 빛 반사(밝은 띠)가 왼쪽에서 오른쪽으로 훑고 지나간 뒤 (0.7초)
 *  3) 앱 화면이 준비되면 부드럽게 사라진다 (0.24초).
 * 웹 버전은 페이지가 그려질 준비가 될 때까지(최대 6초) 잡아 둔다.
 */
class SplashView(context: Context, private val holdUntil: () -> Boolean) : View(context) {
    private val logo: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.splash_logo)
    private val bg = ContextCompat.getColor(context, R.color.splash_bg)
    private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) }
    private val src = android.graphics.Rect(0, 0, logo.width, logo.height)
    private val dst = RectF()
    private val matrix = Matrix()
    private var startMs = 0L
    private var fadeFromMs = -1L
    private var running = false

    init { isClickable = true }       // 시작 화면이 떠 있는 동안 아래 화면이 눌리지 않게

    private fun easeOutCubic(x: Float) = 1f - (1f - x).pow(3)
    private fun easeInOutCubic(x: Float) = if (x < .5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f

    fun begin() {
        startMs = SystemClock.uptimeMillis()
        running = true
        postOnAnimation(tick)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis() - startMs
            if (fadeFromMs < 0 && now >= MIN_MS && (holdUntil() || now > MAX_MS)) fadeFromMs = now
            if (fadeFromMs >= 0) {
                val f = ((now - fadeFromMs) / FADE_MS).coerceIn(0f, 1f)
                alpha = 1f - f
                if (f >= 1f) { finish(); return }
            }
            invalidate()
            postOnAnimation(this)
        }
    }

    private fun finish() {
        running = false
        (parent as? ViewGroup)?.removeView(this)
    }

    override fun onDetachedFromWindow() { running = false; super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(bg)
        val now = (SystemClock.uptimeMillis() - startMs).toFloat()
        val w = width.toFloat(); val h = height.toFloat()
        val size = min(w, h) * LOGO_FRACTION
        val intro = easeOutCubic((now / INTRO_MS).coerceIn(0f, 1f))
        val scale = 0.7f + 0.3f * intro
        val a = ((now / (INTRO_MS * 0.6f)).coerceIn(0f, 1f) * 255f).toInt()
        val half = size * scale / 2f
        val cx = w / 2f; val cy = h / 2f
        dst.set(cx - half, cy - half, cx + half, cy + half)

        val layer = canvas.saveLayer(dst, null)
        logoPaint.alpha = a
        canvas.drawBitmap(logo, src, dst, logoPaint)

        // 빛 반사: 로고가 있는 곳(SRC_ATOP)에만 비스듬한 밝은 띠가 지나간다
        val p = ((now - SWEEP_AT_MS) / SWEEP_MS).coerceIn(0f, 1f)
        if (p > 0f && p < 1f) {
            // 띠를 SHEEN_ANGLE 도 기울여 왼쪽 위에서 오른쪽 아래로 대각선으로 훑는다.
            // 기울인 축 위에서 로고(정사각형)가 차지하는 길이만큼을 덮도록 이동 범위를 잡는다.
            val rad = Math.toRadians(SHEEN_ANGLE.toDouble())
            val reach = dst.width() * (Math.cos(rad) + Math.sin(rad)).toFloat()
            val band = dst.width() * 0.5f
            val e = easeInOutCubic(p)
            val center = cx - (reach + band) / 2f + (reach + band) * e
            val shader = LinearGradient(
                center - band / 2f, cy, center + band / 2f, cy,
                intArrayOf(0x00FFFFFF, 0x66FFFFFF, 0xDDFFFFFF.toInt(), 0x66FFFFFF, 0x00FFFFFF),
                floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f), Shader.TileMode.CLAMP,
            )
            matrix.setRotate(SHEEN_ANGLE, cx, cy)
            shader.setLocalMatrix(matrix)
            sheenPaint.shader = shader
            canvas.drawRect(dst, sheenPaint)
        }
        canvas.restoreToCount(layer)
    }

    companion object {
        private const val LOGO_FRACTION = 0.40f      // 화면 짧은 변의 40%
        private const val INTRO_MS = 420f
        private const val SWEEP_AT_MS = 260f
        private const val SWEEP_MS = 700f
        private const val MIN_MS = 1150L
        private const val MAX_MS = 6000L
        private const val FADE_MS = 240f
        private const val SHEEN_ANGLE = 35f          // 빛 반사 띠의 기울기(도): 왼쪽 위 → 오른쪽 아래 대각선

        /** 화면 맨 위에 시작 화면을 얹는다. holdUntil 이 true 가 될 때(그리고 최소 시간이 지난 뒤) 사라진다. */
        fun show(activity: Activity, holdUntil: () -> Boolean = { true }) {
            val v = SplashView(activity, holdUntil)
            activity.addContentView(v, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            v.begin()
        }
    }
}
