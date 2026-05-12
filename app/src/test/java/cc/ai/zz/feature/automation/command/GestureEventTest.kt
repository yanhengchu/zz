package cc.ai.zz.feature.automation.command

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureEventTest {
    @Test
    fun gestureEvent_usesExpectedDefaults() {
        val event = GestureEvent(action = GestureEvent.ACT_SWIPE_UP)

        assertEquals(GestureEvent.ACT_SWIPE_UP, event.action)
        assertEquals(0L, event.startTime)
        assertEquals(0L, event.periodTime)
        assertEquals(0f, event.x)
        assertEquals(0f, event.y)
    }

    @Test
    fun gestureEvent_preservesProvidedValues() {
        val event = GestureEvent(
            action = GestureEvent.ACT_CLICK_BACK,
            startTime = 5_000L,
            periodTime = 7_500L,
            x = 120.5f,
            y = 300.25f
        )

        assertEquals(GestureEvent.ACT_CLICK_BACK, event.action)
        assertEquals(5_000L, event.startTime)
        assertEquals(7_500L, event.periodTime)
        assertEquals(120.5f, event.x)
        assertEquals(300.25f, event.y)
    }

    @Test
    fun gestureEvent_constantsRemainStable() {
        assertEquals(300L, GestureEvent.GESTURE_EVENT_DURATION)
        assertEquals(5_000L, GestureEvent.PERIOD_MS)
        assertEquals("ACT_STOP", GestureEvent.ACT_STOP)
        assertEquals("ACT_BACK", GestureEvent.ACT_BACK)
        assertEquals("ACT_SWIPE_UP", GestureEvent.ACT_SWIPE_UP)
        assertEquals("ACT_CLICK_BACK", GestureEvent.ACT_CLICK_BACK)
    }
}
