package cc.ai.zz.feature.automation.plan

import org.junit.Assert.assertEquals
import org.junit.Test

class GesturePlanModelTest {
    @Test
    fun gesturePlan_usesExpectedDefaultFailurePolicy() {
        val plan = GesturePlan(
            name = "test-plan",
            initialDelayMs = 1_000L,
            nextCycleDelayPolicy = NextCycleDelayPolicy.Fixed(1_000L),
            steps = listOf(GestureStep.Back())
        )

        assertEquals(FailurePolicy.ToastOnlyContinue, plan.failurePolicy)
    }

    @Test
    fun swipeUpStep_hasExpectedDefaults() {
        val step = GestureStep.SwipeUp()

        assertEquals(0L, step.delayBeforeMs)
    }

    @Test
    fun backStep_usesProvidedDelay() {
        val step = GestureStep.Back(delayBeforeMs = 3_000L)

        assertEquals(3_000L, step.delayBeforeMs)
    }

    @Test
    fun fixedDelayPolicy_preservesDelayMs() {
        val policy = NextCycleDelayPolicy.Fixed(delayMs = 2_500L)

        assertEquals(2_500L, policy.delayMs)
    }
}
