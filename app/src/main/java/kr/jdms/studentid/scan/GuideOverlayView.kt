package kr.jdms.studentid.scan

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.min

/** 카메라 위의 어두운 막 + 학생증 가이드 틀(세로형) + 인증 체크 표시. */
class GuideOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class State { IDLE, DETECTED, VERIFIED }

    var state: State = State.IDLE
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** 가이드 틀의 화면 좌표 */
    val guideRect = RectF()

    private val d = resources.displayMetrics.density
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(140, 0, 0, 0) }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val checkBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_VERIFIED }
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 4.5f * d
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dimPath = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private val checkPath = Path()
    private val corner = 22f * d

    private var checkProgress = 0f
    private var checkAnimator: ValueAnimator? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 웹 버전과 같은 크기: min(270dp, 화면 72%) x min(420dp, 화면 62%), 화면 중앙
        val gw = min(270f * d, w * 0.72f)
        val gh = min(420f * d, h * 0.62f)
        val cx = w / 2f
        val cy = h / 2f
        guideRect.set(cx - gw / 2, cy - gh / 2, cx + gw / 2, cy + gh / 2)
    }

    fun showCheck() {
        checkAnimator?.cancel()
        checkAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320
            interpolator = OvershootInterpolator(2f)
            addUpdateListener {
                checkProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun hideCheck() {
        checkAnimator?.cancel()
        checkProgress = 0f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        checkAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        // 가이드 안쪽만 뚫린 어두운 막
        dimPath.reset()
        dimPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        dimPath.addRoundRect(guideRect, corner, corner, Path.Direction.CW)
        canvas.drawPath(dimPath, dimPaint)

        when (state) {
            State.IDLE -> {
                strokePaint.color = Color.argb(217, 255, 255, 255)
                strokePaint.strokeWidth = 3f * d
                strokePaint.pathEffect = DashPathEffect(floatArrayOf(10f * d, 8f * d), 0f)
                canvas.drawRoundRect(guideRect, corner, corner, strokePaint)
            }
            State.DETECTED, State.VERIFIED -> {
                val c = if (state == State.DETECTED) COLOR_DETECTED else COLOR_VERIFIED
                glowPaint.color = Color.argb(90, Color.red(c), Color.green(c), Color.blue(c))
                glowPaint.strokeWidth = 12f * d
                canvas.drawRoundRect(guideRect, corner, corner, glowPaint)
                strokePaint.color = c
                strokePaint.strokeWidth = 3.5f * d
                strokePaint.pathEffect = null
                canvas.drawRoundRect(guideRect, corner, corner, strokePaint)
            }
        }

        if (checkProgress > 0f) {
            val s = checkProgress
            val cx = guideRect.centerX()
            val cy = guideRect.centerY()
            canvas.drawCircle(cx, cy, 35f * d * s, checkBgPaint)
            checkPath.reset()
            checkPath.moveTo(cx - 12f * d * s, cy + 0f * d * s)
            checkPath.lineTo(cx - 4f * d * s, cy + 8f * d * s)
            checkPath.lineTo(cx + 12f * d * s, cy - 8f * d * s)
            canvas.drawPath(checkPath, checkPaint)
        }
    }

    companion object {
        val COLOR_DETECTED: Int = Color.parseColor("#387AFF")
        val COLOR_VERIFIED: Int = Color.parseColor("#34A853")
    }
}
