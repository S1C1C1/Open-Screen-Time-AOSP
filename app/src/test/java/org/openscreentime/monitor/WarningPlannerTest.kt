package org.openscreentime.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openscreentime.groups.DailyLimit
import org.openscreentime.groups.AppGroup
import org.openscreentime.downtime.DowntimeSchedule
import org.openscreentime.limits.AppLimit
import org.openscreentime.usage.AppUsageTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class WarningPlannerTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val thursday = LocalDate.of(2026, 11, 5)
    private fun at(time: String, date: LocalDate = thursday): Instant = date.atTime(LocalTime.parse(time)).atZone(zone).toInstant()
    private fun minutes(n: Long) = Duration.ofMinutes(n)
    private val hourLimit = listOf(AppLimit("video", minutes(60)))

    private fun due(
        now: Instant = at("15:00"),
        used: Duration = Duration.ZERO,
        limits: List<AppLimit> = hourLimit,
        downtime: DowntimeSchedule = DowntimeSchedule(enabled = false),
        alwaysAllowed: Set<String> = emptySet(),
        shown: Set<String> = emptySet(),
    ) = WarningPlanner.due(now, zone, listOf(AppUsageTime("video", used)), limits, downtime, { it in alwaysAllowed }, shown)

    @Test
    fun warnsWhenFiveMinutesOrLessAreLeft() {
        assertEquals(emptyList<Warning>(), due(used = minutes(54)))
        assertEquals(listOf(Warning.LimitSoon("video", minutes(5), thursday.toEpochDay())), due(used = minutes(55)))
        assertEquals(listOf(Warning.LimitSoon("video", minutes(1), thursday.toEpochDay())), due(used = minutes(59)))
    }

    @Test
    fun noWarningOnceTheLimitIsReached() {
        assertTrue(due(used = minutes(60)).isEmpty())
    }

    @Test
    fun eachWarningIsShownOnlyOnce() {
        val first = due(used = minutes(56)).single()
        assertTrue(due(used = minutes(57), shown = setOf(first.key)).isEmpty())
    }

    @Test
    fun aNewDayWarnsAgain() {
        val thursdayKey = Warning.LimitSoon("video", minutes(5), thursday.toEpochDay()).key
        val friday = due(now = at("15:00", thursday.plusDays(1)), used = minutes(56), shown = setOf(thursdayKey))
        assertEquals(1, friday.size)
    }

    @Test
    fun alwaysAllowedAppsGetNoWarning() {
        assertTrue(due(used = minutes(56), alwaysAllowed = setOf("video")).isEmpty())
    }

    @Test
    fun weekendLimitIsUsedOnWeekends() {
        val limits = listOf(AppLimit("video", minutes(60), weekend = minutes(120)))
        val saturday = thursday.plusDays(2)
        assertTrue(due(now = at("15:00", saturday), used = minutes(56), limits = limits).isEmpty())
        assertEquals(1, due(now = at("15:00", saturday), used = minutes(116), limits = limits).size)
    }

    @Test
    fun warnsFiveMinutesBeforeDowntimeStarts() {
        val downtime = DowntimeSchedule(enabled = true) // 21:00–07:00
        assertTrue(due(now = at("20:54"), limits = emptyList(), downtime = downtime).isEmpty())
        val warning = due(now = at("20:55"), limits = emptyList(), downtime = downtime).single()
        assertEquals(Warning.DowntimeSoon(thursday.atTime(21, 0).atZone(zone)), warning)
        assertTrue(due(now = at("20:58"), limits = emptyList(), downtime = downtime, shown = setOf(warning.key)).isEmpty())
    }

    @Test
    fun noDowntimeWarningWhileDowntimeIsOn() {
        val downtime = DowntimeSchedule(enabled = true)
        assertTrue(due(now = at("22:00"), limits = emptyList(), downtime = downtime).isEmpty())
    }

    @Test
    fun serviceOnlyRunsWhenThereIsSomethingToWatch() {
        val off = DowntimeSchedule(enabled = false)
        val on = DowntimeSchedule(enabled = true)
        assertFalse(WarningPlanner.hasSomethingToWatch(trackingEnabled = false, limits = hourLimit, downtime = on))
        assertFalse(WarningPlanner.hasSomethingToWatch(trackingEnabled = true, limits = emptyList(), downtime = off))
        assertTrue(WarningPlanner.hasSomethingToWatch(trackingEnabled = true, limits = hourLimit, downtime = off))
        assertTrue(WarningPlanner.hasSomethingToWatch(trackingEnabled = true, limits = emptyList(), downtime = on))
    }

    // --- App groups ---

    private val social = AppGroup(7, "Social", setOf("video", "chat"), DailyLimit(minutes(60)))

    private fun dueForGroup(chat: Duration, video: Duration, alwaysAllowed: Set<String> = emptySet()) = WarningPlanner.due(
        at("15:00"), zone, listOf(AppUsageTime("video", video), AppUsageTime("chat", chat)), emptyList(),
        DowntimeSchedule(enabled = false), { it in alwaysAllowed }, emptySet(), groups = listOf(social),
    )

    @Test
    fun warnsWhenTheGroupsSharedTotalIsNearlyUsed() {
        assertTrue(dueForGroup(chat = minutes(30), video = minutes(24)).isEmpty())
        assertEquals(
            listOf(Warning.GroupLimitSoon(7, "Social", minutes(4), thursday.toEpochDay())),
            dueForGroup(chat = minutes(30), video = minutes(26)),
        )
        assertTrue(dueForGroup(chat = minutes(30), video = minutes(30)).isEmpty())
    }

    @Test
    fun noGroupWarningWhenEveryAppInItIsAlwaysAllowed() {
        assertTrue(dueForGroup(chat = minutes(30), video = minutes(26), alwaysAllowed = setOf("video", "chat")).isEmpty())
    }

    @Test
    fun groupWithALimitIsSomethingToWatch() {
        assertTrue(WarningPlanner.hasSomethingToWatch(true, emptyList(), DowntimeSchedule(enabled = false), listOf(social)))
        assertFalse(
            WarningPlanner.hasSomethingToWatch(true, emptyList(), DowntimeSchedule(enabled = false), listOf(social.copy(limit = null))),
        )
    }
}
