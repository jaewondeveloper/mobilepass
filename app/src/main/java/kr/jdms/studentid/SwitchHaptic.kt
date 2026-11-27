package kr.jdms.studentid

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

object SwitchHaptic {
    // 삼성 기기에서만 존재하는 메서드. 없으면 -1.
    private val index: Int by lazy {
        val cls = android.view.HapticFeedbackConstants::class.java
        for (name in arrayOf("semGetVibrationIndex", "hidden_semGetVibrationIndex")) {
            try {
                val m = cls.getMethod(name, Int::class.javaPrimitiveType)
                return@lazy (m.invoke(null, 27) as? Int) ?: -1
            } catch (_: Throwable) {}
        }
        -1
    }

    fun perform(view: View, old: Boolean, new: Boolean) {
        if (old == new || !view.hasWindowFocus() || !view.isShown) return
        if (index != -1) view.performHapticFeedback(index)
        else if (Build.VERSION.SDK_INT >= 30)
            view.performHapticFeedback(if (new) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT)
        else view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}
