package org.openscreentime.usage

import android.app.usage.UsageEvents.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class UsageEventMappingTest {

    private fun map(type: Int) = SystemUsageEventSource.toUsageEvent(type, 1_000L, "app", "Main")
    private val time = Instant.ofEpochMilli(1_000L)

    @Test
    fun appEvents() {
        assertEquals(UsageEvent.AppResumed(time, "app", "Main"), map(Event.ACTIVITY_RESUMED))
        assertEquals(UsageEvent.AppPaused(time, "app", "Main"), map(Event.ACTIVITY_PAUSED))
        assertEquals(UsageEvent.AppPaused(time, "app", "Main"), map(Event.ACTIVITY_STOPPED))
    }

    @Test
    fun screenOffEvents() {
        assertEquals(UsageEvent.ScreenOff(time), map(Event.SCREEN_NON_INTERACTIVE))
        assertEquals(UsageEvent.ScreenOff(time), map(Event.KEYGUARD_SHOWN))
        assertEquals(UsageEvent.ScreenOff(time), map(Event.DEVICE_SHUTDOWN))
    }

    @Test
    fun otherEventsAreIgnored() {
        assertNull(map(Event.SCREEN_INTERACTIVE))
        assertNull(map(Event.USER_INTERACTION))
        assertNull(map(Event.CONFIGURATION_CHANGE))
    }
}
