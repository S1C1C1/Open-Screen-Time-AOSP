package org.openscreentime.limits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

class AppLimitTest {

    private val thursday = LocalDate.of(2026, 11, 5)
    private val friday = thursday.plusDays(1)
    private val saturday = thursday.plusDays(2)
    private val sunday = thursday.plusDays(3)
    private val monday = thursday.plusDays(4)
    private fun minutes(n: Long) = Duration.ofMinutes(n)

    @Test
    fun sameLimitEveryDay() {
        val limit = AppLimit("video", weekday = minutes(60))
        listOf(thursday, saturday, sunday).forEach { assertEquals(minutes(60), limit.limitOn(it)) }
    }

    @Test
    fun weekendLimitAppliesOnSaturdayAndSundayOnly() {
        val limit = AppLimit("video", weekday = minutes(60), weekend = minutes(120))
        assertEquals(minutes(60), limit.limitOn(thursday))
        assertEquals(minutes(60), limit.limitOn(friday))
        assertEquals(minutes(120), limit.limitOn(saturday))
        assertEquals(minutes(120), limit.limitOn(sunday))
        assertEquals(minutes(60), limit.limitOn(monday))
    }

    @Test
    fun timeLeftAndReached() {
        val limit = AppLimit("video", weekday = minutes(60))
        assertEquals(minutes(60), limit.timeLeft(thursday, Duration.ZERO))
        assertEquals(minutes(15), limit.timeLeft(thursday, minutes(45)))
        assertFalse(limit.isReached(thursday, minutes(59)))
        assertTrue(limit.isReached(thursday, minutes(60)))
        assertEquals(Duration.ZERO, limit.timeLeft(thursday, minutes(90)))
        assertTrue(limit.isReached(thursday, minutes(90)))
    }

    @Test
    fun aNewDayStartsFromTheFullLimit() {
        // Limits are compared with each day's own usage, so midnight resets them.
        val limit = AppLimit("video", weekday = minutes(60))
        assertTrue(limit.isReached(thursday, minutes(60)))
        assertEquals(minutes(60), limit.timeLeft(friday, Duration.ZERO))
    }

    @Test
    fun validLengths() {
        assertTrue(LimitRules.isValid(minutes(15)))
        assertTrue(LimitRules.isValid(minutes(90)))
        assertTrue(LimitRules.isValid(Duration.ofHours(23).plusMinutes(45)))
    }

    @Test
    fun invalidLengths() {
        assertFalse(LimitRules.isValid(Duration.ZERO))
        assertFalse(LimitRules.isValid(minutes(10)))
        assertFalse(LimitRules.isValid(minutes(20)))
        assertFalse(LimitRules.isValid(Duration.ofHours(24)))
        assertFalse(LimitRules.isValid(minutes(15).plusSeconds(30)))
        assertFalse(LimitRules.isValid(AppLimit("x", minutes(60), weekend = minutes(5))))
    }

    @Test
    fun snapRoundsToTheGridWithinRange() {
        assertEquals(minutes(15), LimitRules.snap(Duration.ZERO))
        assertEquals(minutes(15), LimitRules.snap(minutes(20)))
        assertEquals(minutes(30), LimitRules.snap(minutes(23)))
        assertEquals(Duration.ofHours(23).plusMinutes(45), LimitRules.snap(Duration.ofHours(30)))
    }
}
