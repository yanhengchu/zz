package cc.ai.zz.feature.automation.plan

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureRuntimeResolverTest {
    @Test
    fun resolveNextCycleDelay_returnsFixedDelayForFixedPolicy() {
        val delay = GestureRuntimeResolver.resolveNextCycleDelay(
            policy = NextCycleDelayPolicy.Fixed(2_500L)
        )

        assertEquals(2_500L, delay)
    }

}
