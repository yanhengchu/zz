package cc.ai.zz.feature.ocr.rule

import android.graphics.PointF
import cc.ai.zz.app.MyApp
import cc.ai.zz.feature.automation.executor.GestureExecutor

class OcrActionExecutor(
    private val executorProvider: () -> GestureExecutor?,
    private val onShowMessage: (String) -> Unit
) {
    fun execute(rule: OcrActionRule, useElseTarget: Boolean = false): Boolean {
        return when (val action = rule.action) {
            OcrRuleAction.Wait -> true
            OcrRuleAction.Back -> {
                val executor = requireExecutor() ?: return false
                executor.back()
                true
            }

            OcrRuleAction.Swipe -> {
                val executor = requireExecutor() ?: return false
                executor.swipeUp()
                true
            }

            is OcrRuleAction.Click -> {
                val executor = requireExecutor() ?: return false
                val clickTarget = if (useElseTarget) action.elseTarget ?: return false else action.target
                val position = resolveRatioClickPosition(clickTarget)
                executor.click(position.x, position.y)
                true
            }
        }
    }

    private fun requireExecutor(): GestureExecutor? {
        val executor = executorProvider()
        if (executor != null) return executor
        onShowMessage("无障碍不可用，跳过 OCR 动作")
        return null
    }

    private fun resolveRatioClickPosition(target: OcrClickTarget): PointF {
        val displayMetrics = MyApp.context.resources.displayMetrics
        val x = displayMetrics.widthPixels * target.xRatio.coerceIn(0f, 1f)
        val y = displayMetrics.heightPixels * target.yRatio.coerceIn(0f, 1f)
        return PointF(x, y)
    }
}
