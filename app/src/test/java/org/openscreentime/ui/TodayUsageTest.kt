package org.openscreentime.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.openscreentime.usage.AppUsageTime
import java.time.Duration

class TodayUsageTest {

    private fun app(pkg: String, minutes: Long) = AppUsageTime(pkg, Duration.ofMinutes(minutes))

    @Test
    fun totalCountsEveryAppButOnlyTopFiveAreListed() {
        val apps = listOf(app("a", 1), app("b", 6), app("c", 3), app("d", 5), app("e", 2), app("f", 4))
        val usage = TodayUsage.from(apps) { it.uppercase() }

        assertEquals(Duration.ofMinutes(21), usage.total)
        assertEquals(listOf("B", "D", "F", "C", "E"), usage.topApps.map { it.label })
    }

    @Test
    fun emptyDay() {
        val usage = TodayUsage.from(emptyList()) { it }
        assertEquals(Duration.ZERO, usage.total)
        assertEquals(emptyList<AppTime>(), usage.topApps)
    }
}
