package org.openscreentime.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ForegroundAppTest {

    private fun t(seconds: Long) = Instant.ofEpochSecond(1_000 + seconds)
    private fun resumed(s: Long, pkg: String, cls: String = "Main") = UsageEvent.AppResumed(t(s), pkg, cls)
    private fun paused(s: Long, pkg: String, cls: String = "Main") = UsageEvent.AppPaused(t(s), pkg, cls)

    @Test
    fun theLastOpenedAppIsOnScreen() {
        assertEquals("maps", foregroundApp(listOf(resumed(0, "video"), paused(5, "video"), resumed(5, "maps"))))
    }

    @Test
    fun noAppAfterGoingHome() {
        assertNull(foregroundApp(listOf(resumed(0, "video"), paused(5, "video"))))
    }

    @Test
    fun noAppAfterScreenOff() {
        assertNull(foregroundApp(listOf(resumed(0, "video"), UsageEvent.ScreenOff(t(5)))))
    }

    @Test
    fun movingBetweenScreensOfOneAppKeepsIt() {
        assertEquals(
            "mail",
            foregroundApp(listOf(resumed(0, "mail", "Inbox"), resumed(3, "mail", "Message"), paused(3, "mail", "Inbox"))),
        )
    }

    @Test
    fun splitScreenFallsBackToTheOtherApp() {
        assertEquals("video", foregroundApp(listOf(resumed(0, "video"), resumed(2, "notes"), paused(9, "notes"))))
    }

    @Test
    fun noEvents() {
        assertNull(foregroundApp(emptyList()))
    }
}
