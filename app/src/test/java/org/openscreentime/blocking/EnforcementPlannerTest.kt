package org.openscreentime.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openscreentime.schedules.SchedulePreset
import org.openscreentime.schedules.AppSchedule
import org.openscreentime.groups.DailyLimit
import org.openscreentime.groups.AppGroup
import org.openscreentime.downtime.DowntimeSchedule
import org.openscreentime.limits.AppLimit
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class EnforcementPlannerTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val thursday = LocalDate.of(2026, 11, 5)
    private fun at(time: String): Instant = thursday.atTime(LocalTime.parse(time)).atZone(zone).toInstant()
    private fun minutes(n: Long) = Duration.ofMinutes(n)

    private val apps = setOf("video", "maps", "dialer", "launcher")
    private val allowed = setOf("dialer", "launcher")

    private fun plan(
        now: Instant = at("15:00"),
        used: Map<String, Duration> = emptyMap(),
        limits: List<AppLimit> = listOf(AppLimit("video", minutes(60))),
        downtime: DowntimeSchedule = DowntimeSchedule(enabled = false),
        overrides: Map<String, AppOverride> = emptyMap(),
        mode: LimitMode = LimitMode.Strict,
        access: Boolean = true,
        foreground: String? = null,
    ) = EnforcementPlanner.plan(
        now, zone, apps, used, limits.associateBy { it.packageName }, downtime, { it in allowed }, overrides, mode, access, foreground,
    )

    @Test
    fun nothingPausedUnderTheLimit() {
        assertEquals(emptySet<String>(), plan(used = mapOf("video" to minutes(30))).pause)
    }

    @Test
    fun appOverItsLimitIsPaused() {
        assertEquals(setOf("video"), plan(used = mapOf("video" to minutes(60))).pause)
    }

    @Test
    fun downtimePausesEverythingButAlwaysAllowedApps() {
        val result = plan(now = at("22:00"), downtime = DowntimeSchedule(enabled = true))
        assertEquals(setOf("video", "maps"), result.pause)
    }

    @Test
    fun extraTimeUnpauses() {
        val result = plan(
            used = mapOf("video" to minutes(70)),
            overrides = mapOf("video" to AppOverride(allowedUntil = mapOf(BlockKeys.LIMIT to at("15:10")))),
        )
        assertEquals(emptySet<String>(), result.pause)
    }

    @Test
    fun reminderModeNeverPauses() {
        val result = plan(used = mapOf("video" to minutes(70)), mode = LimitMode.Reminder, foreground = "video")
        assertEquals(emptySet<String>(), result.pause)
        assertEquals(BlockRequest("video", BlockReason.LimitReached), result.showBlockScreen)
    }

    @Test
    fun blockScreenOnlyForTheOpenBlockedApp() {
        assertEquals(
            BlockRequest("video", BlockReason.LimitReached),
            plan(used = mapOf("video" to minutes(60)), foreground = "video").showBlockScreen,
        )
        assertNull(plan(used = mapOf("video" to minutes(60)), foreground = "maps").showBlockScreen)
        assertNull(plan(used = mapOf("video" to minutes(30)), foreground = "video").showBlockScreen)
        assertNull(plan(now = at("22:00"), downtime = DowntimeSchedule(enabled = true), foreground = "dialer").showBlockScreen)
    }

    @Test
    fun failSafeWithoutUsageAccessPausesAllButAlwaysAllowed() {
        val result = plan(access = false)
        assertEquals(setOf("video", "maps"), result.pause)
        assertNull(result.showBlockScreen)
    }

    @Test
    fun checksAgainWhenTheLimitWillRunOut() {
        assertEquals(Duration.ofSeconds(20), plan(used = mapOf("video" to minutes(60).minusSeconds(20))).checkAgainIn)
    }

    @Test
    fun checksAgainWhenExtraTimeEnds() {
        val result = plan(
            now = at("15:09:50"),
            used = mapOf("video" to minutes(70)),
            overrides = mapOf("video" to AppOverride(allowedUntil = mapOf(BlockKeys.LIMIT to at("15:10")))),
        )
        assertEquals(Duration.ofSeconds(10), result.checkAgainIn)
    }

    @Test
    fun checksAgainWhenDowntimeStarts() {
        assertEquals(
            Duration.ofSeconds(15),
            plan(now = at("20:59:45"), limits = emptyList(), downtime = DowntimeSchedule(enabled = true)).checkAgainIn,
        )
    }

    @Test
    fun waitStaysBetweenFiveAndThirtySeconds() {
        assertEquals(Duration.ofSeconds(30), plan(limits = emptyList()).checkAgainIn)
        assertEquals(Duration.ofSeconds(5), plan(used = mapOf("video" to minutes(60).minusSeconds(1))).checkAgainIn)
    }

    // --- App groups ---

    private val group = AppGroup(1, "Fun", setOf("video", "maps", "dialer"), DailyLimit(minutes(60)))

    private fun planWithGroup(used: Map<String, Duration>, foreground: String? = null) = EnforcementPlanner.plan(
        at("15:00"), zone, apps, used, emptyMap(), DowntimeSchedule(enabled = false), { it in allowed }, emptyMap(),
        LimitMode.Strict, true, foreground, groups = listOf(group),
    )

    @Test
    fun groupLimitPausesEveryAppInTheGroupExceptAlwaysAllowed() {
        assertEquals(emptySet<String>(), planWithGroup(mapOf("video" to minutes(30), "maps" to minutes(29))).pause)
        val result = planWithGroup(mapOf("video" to minutes(30), "maps" to minutes(30)), foreground = "maps")
        assertEquals(setOf("video", "maps"), result.pause)
        assertEquals(BlockRequest("maps", BlockReason.LimitReached, "Fun"), result.showBlockScreen)
    }

    @Test
    fun checksAgainWhenTheGroupLimitWillRunOut() {
        val used = mapOf("video" to minutes(30), "maps" to minutes(30).minusSeconds(12))
        assertEquals(Duration.ofSeconds(12), planWithGroup(used).checkAgainIn)
    }

    // --- Schedules ---

    private val work = AppSchedule(
        id = 1,
        name = "Work",
        days = SchedulePreset.WorkSchool.days,
        window = SchedulePreset.WorkSchool.window,
        packages = setOf("video", "dialer"),
    )

    private fun planWithSchedule(now: Instant, foreground: String? = null) = EnforcementPlanner.plan(
        now, zone, apps, emptyMap(), emptyMap(), DowntimeSchedule(enabled = false), { it in allowed }, emptyMap(),
        LimitMode.Strict, true, foreground, schedules = listOf(work),
    )

    @Test
    fun aSchedulePausesItsAppsExceptAlwaysAllowedOnes() {
        val result = planWithSchedule(at("10:00"), foreground = "video")
        assertEquals(setOf("video"), result.pause)
        assertEquals(BlockRequest("video", BlockReason.Scheduled, "Work", BlockKeys.schedule(1)), result.showBlockScreen)
        assertEquals(emptySet<String>(), planWithSchedule(at("18:00")).pause)
    }

    @Test
    fun checksAgainWhenAScheduleStartsOrEnds() {
        assertEquals(Duration.ofSeconds(7), planWithSchedule(at("07:59:53")).checkAgainIn)
        assertEquals(Duration.ofSeconds(9), planWithSchedule(at("16:59:51")).checkAgainIn)
    }
}
