package cc.ai.zz.feature.automation.service

import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.util.Log
import cc.ai.zz.feature.automation.command.GestureEvent
import cc.ai.zz.feature.automation.executor.GestureExecutor
import cc.ai.zz.feature.automation.plan.GesturePlan
import cc.ai.zz.feature.automation.plan.GestureRuntimeResolver
import cc.ai.zz.feature.automation.plan.GestureStep
import cc.ai.zz.feature.automation.plan.NextCycleDelayPolicy
import kotlinx.coroutines.Runnable

class PeriodicTaskRunner(
    private val executorProvider: () -> GestureExecutor?,
    private val floatingPositionProvider: () -> Point?,
    private val onShowMessage: (String) -> Unit,
    private val onShowCountdown: (Long) -> Unit,
    private val onAccessibilityLost: () -> Unit,
    private val onPrepareOverlay: (Long) -> Unit
) {
    companion object {
        private const val TAG = "PeriodicTaskRunner"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var periodicRunnable: Runnable? = null
    private var taskSessionId = 0L

    fun start(event: GestureEvent, plan: GesturePlan) {
        taskSessionId += 1
        val sessionId = taskSessionId
        handler.removeCallbacksAndMessages(null)

        periodicRunnable = Runnable {
            executePlanCycle(plan, event, 0, sessionId)
        }

        event.periodTime = plan.initialDelayMs
        periodicRunnable?.let { handler.postDelayed(it, plan.initialDelayMs) }
        onPrepareOverlay(plan.initialDelayMs)
    }

    fun stop() {
        taskSessionId += 1
        handler.removeCallbacksAndMessages(null)
        periodicRunnable = null
    }

    fun release() {
        stop()
    }

    private fun executePlanCycle(plan: GesturePlan, event: GestureEvent, stepIndex: Int, sessionId: Long) {
        if (sessionId != taskSessionId) return
        if (stepIndex >= plan.steps.size) {
            scheduleNextPlanCycle(plan, event, sessionId)
            return
        }
        val step = plan.steps[stepIndex]
        handler.postDelayed({
            if (sessionId != taskSessionId) return@postDelayed
            if (!executeStep(step, event)) return@postDelayed
            executePlanCycle(plan, event, stepIndex + 1, sessionId)
        }, step.delayBeforeMs)
    }

    private fun executeStep(step: GestureStep, event: GestureEvent): Boolean {
        when (step) {
            is GestureStep.SwipeUp -> {
                val executor = requireExecutor() ?: return false
                event.periodTime = event.startTime
                executor.swipeUp()
                onShowCountdown(event.periodTime)
            }

            is GestureStep.ClickFromFloatingWindow -> {
                val executor = requireExecutor() ?: return false
                val pos = floatingPositionProvider()
                if (pos == null) {
                    onShowMessage("点击位置不可用，跳过本轮点击")
                    return true
                }
                val clickPosition = GestureRuntimeResolver.resolveClickPosition(pos.x, pos.y, step)
                executor.click(clickPosition.x, clickPosition.y)
                onShowCountdown(event.startTime)
            }

            is GestureStep.Back -> {
                val executor = requireExecutor() ?: return false
                executor.back()
            }
        }
        return true
    }

    private fun scheduleNextPlanCycle(plan: GesturePlan, event: GestureEvent, sessionId: Long) {
        if (sessionId != taskSessionId) return
        val nextDelay = GestureRuntimeResolver.resolveNextCycleDelay(plan.nextCycleDelayPolicy, event.periodTime)
        if (plan.nextCycleDelayPolicy is NextCycleDelayPolicy.Fixed) {
            onShowCountdown(nextDelay)
        }
        periodicRunnable?.let { handler.postDelayed(it, nextDelay) }
    }

    private fun requireExecutor(): GestureExecutor? {
        val executor = executorProvider()
        if (executor != null) return executor
        Log.w(TAG, "stop periodic task because accessibility is unavailable")
        stop()
        onAccessibilityLost()
        return null
    }
}
