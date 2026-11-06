package org.openscreentime.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openscreentime.downtime.DowntimeSchedule
import org.openscreentime.downtime.DowntimeWindow
import org.openscreentime.limits.AppLimit
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class BlockRulesTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val thursday = LocalDate.of(2026, 11, 5)
    private fun at(time: String, date: LocalDate = thursday): Instant =
        date.atTime(LocalTime.parse(time)).atZone(zone).toInstant()
    private fun minutes(n: Long) = Duration.ofMinutes(n)

    private val hourLimit = AppLimit("video", minutes(60))
    private val noDowntime = DowntimeSchedule(enabled = false)
    private val nightDowntime = DowntimeSchedule(enabled = true) // 21:00–07:00 every day

    private fun decide(
        now: Instant = at("15:00"),
        used: Duration = Duration.ZERO,
        limit: AppLimit? = hourLimit,
        downtime: DowntimeSchedule = noDowntime,
        alwaysAllowed: Boolean = false,
        override: AppOverride = AppOverride(),
        mode: LimitMode = LimitMode.Strict,
    ) = BlockRules.decide(now, zone, used, limit, downtime, alwaysAllowed, override, mode)

    private val midnight = thursday.plusDays(1).atStartOfDay(zone)

    @Test
    fun underTheLimitIsAllowed() {
        assertEquals(BlockDecision.Allowed, decide(used = minutes(59)))
    }

    @Test
    fun reachingTheLimitBlocksUntilMidnight() {
        assertEquals(
            BlockDecision.Blocked(BlockReason.LimitReached, midnight, graceAvailable = false),
            decide(used = minutes(60)),
        )
    }

    @Test
    fun noLimitMeansNeverBlockedByLimit() {
        assertEquals(BlockDecision.Allowed, decide(used = Duration.ofHours(10), limit = null))
    }

    @Test
    fun aNewDayStartsUnblocked() {
        // Yesterday's use doesn't count today: usedToday is today's total only.
        assertEquals(BlockDecision.Allowed, decide(now = at("00:01", thursday.plusDays(1)), used = Duration.ZERO))
    }

    @Test
    fun downtimeBlocksEvenUnderTheLimitUntilItEnds() {
        val decision = decide(now = at("22:00"), used = Duration.ZERO, downtime = nightDowntime)
        assertEquals(
            BlockDecision.Blocked(BlockReason.Downtime, thursday.plusDays(1).atTime(7, 0).atZone(zone), false),
            decision,
        )
    }

    @Test
    fun downtimeBlocksAppsWithoutALimit() {
        assertTrue(decide(now = at("23:00"), limit = null, downtime = nightDowntime) is BlockDecision.Blocked)
    }

    @Test
    fun downtimeIsShownBeforeTheLimitWhenBothApply() {
        val decision = decide(now = at("22:00"), used = minutes(90), downtime = nightDowntime) as BlockDecision.Blocked
        assertEquals(BlockReason.Downtime, decision.reason)
    }

    @Test
    fun alwaysAllowedAppsAreNeverBlocked() {
        assertEquals(BlockDecision.Allowed, decide(used = minutes(500), alwaysAllowed = true))
        assertEquals(BlockDecision.Allowed, decide(now = at("23:00"), downtime = nightDowntime, alwaysAllowed = true))
    }

    @Test
    fun extraTimeAllowsUntilItEnds() {
        val until = at("15:15")
        val override = AppOverride(allowedUntil = until)
        assertEquals(BlockDecision.AllowedUntil(until), decide(now = at("15:00"), used = minutes(60), override = override))
        assertEquals(BlockDecision.AllowedUntil(until), decide(now = at("15:14"), used = minutes(60), override = override))
        assertTrue(decide(now = at("15:15"), used = minutes(60), override = override) is BlockDecision.Blocked)
    }

    @Test
    fun oldExtraTimeDoesNothingWhenNotBlocked() {
        val override = AppOverride(allowedUntil = at("15:15"))
        assertEquals(BlockDecision.Allowed, decide(now = at("15:00"), used = minutes(10), override = override))
    }

    @Test
    fun graceIsOfferedOncePerDayOnlyInGraceMode() {
        val blockedGrace = decide(used = minutes(60), mode = LimitMode.Grace) as BlockDecision.Blocked
        assertTrue(blockedGrace.graceAvailable)

        val usedToday = AppOverride(graceUsedOn = thursday)
        assertFalse((decide(used = minutes(60), override = usedToday, mode = LimitMode.Grace) as BlockDecision.Blocked).graceAvailable)

        val usedYesterday = AppOverride(graceUsedOn = thursday.minusDays(1))
        assertTrue((decide(used = minutes(60), override = usedYesterday, mode = LimitMode.Grace) as BlockDecision.Blocked).graceAvailable)

        assertFalse((decide(used = minutes(60), mode = LimitMode.Strict) as BlockDecision.Blocked).graceAvailable)
        assertFalse((decide(used = minutes(60), mode = LimitMode.Reminder) as BlockDecision.Blocked).graceAvailable)
    }

    @Test
    fun unlockDurations() {
        val now = at("15:00")
        val limitBlock = BlockDecision.Blocked(BlockReason.LimitReached, midnight, false)
        assertEquals(at("15:15"), BlockRules.unlockEnd(now, UnlockDuration.FifteenMinutes, limitBlock))
        assertEquals(at("16:00"), BlockRules.unlockEnd(now, UnlockDuration.OneHour, limitBlock))
        assertEquals(midnight.toInstant(), BlockRules.unlockEnd(now, UnlockDuration.RestOfDay, limitBlock))

        val downtimeEnd = thursday.plusDays(1).atTime(7, 0).atZone(zone)
        val downtimeBlock = BlockDecision.Blocked(BlockReason.Downtime, downtimeEnd, false)
        assertEquals(downtimeEnd.toInstant(), BlockRules.unlockEnd(at("22:00"), UnlockDuration.RestOfDay, downtimeBlock))
    }

    @Test
    fun weekendLimitApplies() {
        val limit = AppLimit("video", minutes(60), weekend = minutes(120))
        val saturday = thursday.plusDays(2)
        assertEquals(BlockDecision.Allowed, decide(now = at("15:00", saturday), used = minutes(90), limit = limit))
        assertTrue(decide(now = at("15:00", saturday), used = minutes(120), limit = limit) is BlockDecision.Blocked)
    }

    @Test
    fun customDowntimeDayOff() {
        val schedule = DowntimeSchedule(enabled = true, sameEveryDay = false)
            .withDay(java.time.DayOfWeek.THURSDAY, null)
            .withDay(java.time.DayOfWeek.WEDNESDAY, DowntimeWindow(LocalTime.of(21, 0), LocalTime.of(7, 0)))
        // Wednesday night's downtime runs into Thursday morning, but Thursday night is free.
        assertTrue(decide(now = at("06:00"), downtime = schedule, limit = null) is BlockDecision.Blocked)
        assertEquals(BlockDecision.Allowed, decide(now = at("22:00"), downtime = schedule, limit = null))
    }
}
