package cc.ai.zz.feature.automation.plan

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureRuntimeResolverTest {
    @Test
    fun resolveClickPosition_appliesStepOffsets() {
        val clickPosition = GestureRuntimeResolver.resolveClickPosition(
            anchorX = 300,
            anchorY = 500,
            step = GestureStep.ClickFromFloatingWindow(offsetX = -10, offsetY = 20)
        )

        assertEquals(290f, clickPosition.x)
        assertEquals(520f, clickPosition.y)
    }

    @Test
    fun resolveClickPosition_clampsNegativeCoordinatesToZero() {
        val clickPosition = GestureRuntimeResolver.resolveClickPosition(
            anchorX = 5,
            anchorY = 6,
            step = GestureStep.ClickFromFloatingWindow(offsetX = -20, offsetY = -30)
        )

        assertEquals(0f, clickPosition.x)
        assertEquals(0f, clickPosition.y)
    }

    @Test
    fun resolveNextCycleDelay_returnsFixedDelayForFixedPolicy() {
        val delay = GestureRuntimeResolver.resolveNextCycleDelay(
            policy = NextCycleDelayPolicy.Fixed(2_500L),
            eventPeriodTime = 9_000L
        )

        assertEquals(2_500L, delay)
    }

    @Test
    fun resolveNextCycleDelay_usesEventPeriodTimeForDynamicPolicy() {
        val delay = GestureRuntimeResolver.resolveNextCycleDelay(
            policy = NextCycleDelayPolicy.UseEventPeriodTime,
            eventPeriodTime = 9_000L
        )

        assertEquals(9_000L, delay)
    }
}
