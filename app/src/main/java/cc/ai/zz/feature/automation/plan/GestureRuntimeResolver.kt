package cc.ai.zz.feature.automation.plan

data class GestureClickPosition(
    val x: Float,
    val y: Float
)

object GestureRuntimeResolver {
    fun resolveClickPosition(anchorX: Int, anchorY: Int, step: GestureStep.ClickFromFloatingWindow): GestureClickPosition {
        return GestureClickPosition(
            x = (anchorX + step.offsetX).coerceAtLeast(0).toFloat(),
            y = (anchorY + step.offsetY).coerceAtLeast(0).toFloat()
        )
    }

    fun resolveNextCycleDelay(policy: NextCycleDelayPolicy, eventPeriodTime: Long): Long {
        return when (policy) {
            is NextCycleDelayPolicy.Fixed -> policy.delayMs
            NextCycleDelayPolicy.UseEventPeriodTime -> eventPeriodTime
        }
    }
}
