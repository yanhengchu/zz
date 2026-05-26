package cc.ai.zz.feature.automation.service

import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.util.Log
import cc.ai.zz.feature.automation.executor.GestureExecutor
import cc.ai.zz.feature.automation.plan.GestureRuntimeResolver
import cc.ai.zz.feature.automation.plan.GestureStep

class ContinuousClickRunner(
    private val executorProvider: () -> GestureExecutor?,
    private val floatingPositionProvider: () -> Point?,
    private val onShowMessage: (String) -> Unit,
    private val onAccessibilityLost: () -> Unit
) {
    companion object {
        private const val TAG = "ContinuousClickRunner"
        private const val CLICK_INTERVAL_MS = 50L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val clickStep = GestureStep.ClickFromFloatingWindow()
    private var sessionId = 0L
    private var hasShownMissingPosition = false

    var isActive = false
        private set

    fun start() {
        if (isActive) return
        sessionId += 1
        val currentSessionId = sessionId
        hasShownMissingPosition = false
        isActive = true
        handler.post { clickOnceAndScheduleNext(currentSessionId) }
    }

    fun stop() {
        sessionId += 1
        isActive = false
        hasShownMissingPosition = false
        handler.removeCallbacksAndMessages(null)
    }

    fun release() {
        stop()
    }

    private fun clickOnceAndScheduleNext(expectedSessionId: Long) {
        if (expectedSessionId != sessionId || !isActive) return
        val executor = executorProvider()
        if (executor == null) {
            Log.w(TAG, "stop continuous click because accessibility is unavailable")
            stop()
            onAccessibilityLost()
            return
        }
        val pos = floatingPositionProvider()
        if (pos == null) {
            if (!hasShownMissingPosition) {
                onShowMessage("点击位置不可用，连点暂未执行")
                hasShownMissingPosition = true
            }
        } else {
            val clickPosition = GestureRuntimeResolver.resolveClickPosition(pos.x, pos.y, clickStep)
            executor.click(clickPosition.x, clickPosition.y)
        }
        handler.postDelayed({ clickOnceAndScheduleNext(expectedSessionId) }, CLICK_INTERVAL_MS)
    }
}
