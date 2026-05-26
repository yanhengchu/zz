package cc.ai.zz.feature.automation.plan

import cc.ai.zz.feature.automation.command.GestureEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GesturePlanFactoryTest {
    @Test
    fun buildSwipeUpPlan_returnsExpectedPlan() {
        val event = GestureEvent(
            action = GestureEvent.ACT_SWIPE_UP,
            startTime = 15_000L
        )

        val plan = GesturePlanFactory.buildSwipeUpPlan(event)

        assertEquals("开始固定15s上滑", plan.name)
        assertEquals(GesturePlanFactory.INITIAL_DELAY_MS, plan.initialDelayMs)
        assertEquals(NextCycleDelayPolicy.Fixed(event.startTime), plan.nextCycleDelayPolicy)
        assertEquals(FailurePolicy.ToastOnlyContinue, plan.failurePolicy)
        assertEquals(1, plan.steps.size)

        val step = plan.steps.single()
        assertTrue(step is GestureStep.SwipeUp)

        step as GestureStep.SwipeUp
        assertEquals(0L, step.delayBeforeMs)
    }

}
