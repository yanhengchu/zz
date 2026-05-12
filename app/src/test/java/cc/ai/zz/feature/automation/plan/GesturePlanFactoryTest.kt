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

    @Test
    fun buildClickBackPlan_returnsExpectedPlan() {
        val event = GestureEvent(
            action = GestureEvent.ACT_CLICK_BACK,
            startTime = 8_000L
        )

        val plan = GesturePlanFactory.buildClickBackPlan(event)

        assertEquals("开始固定8s点击", plan.name)
        assertEquals(GesturePlanFactory.INITIAL_DELAY_MS, plan.initialDelayMs)
        assertEquals(
            NextCycleDelayPolicy.Fixed(GesturePlanFactory.CLICK_BACK_NEXT_CYCLE_DELAY_MS),
            plan.nextCycleDelayPolicy
        )
        assertEquals(FailurePolicy.ToastOnlyContinue, plan.failurePolicy)
        assertEquals(2, plan.steps.size)

        val clickStep = plan.steps[0]
        assertTrue(clickStep is GestureStep.ClickFromFloatingWindow)

        clickStep as GestureStep.ClickFromFloatingWindow
        assertEquals(0L, clickStep.delayBeforeMs)
        assertEquals(-10, clickStep.offsetX)
        assertEquals(-10, clickStep.offsetY)

        val backStep = plan.steps[1]
        assertTrue(backStep is GestureStep.Back)

        backStep as GestureStep.Back
        assertEquals(event.startTime, backStep.delayBeforeMs)
    }
}
