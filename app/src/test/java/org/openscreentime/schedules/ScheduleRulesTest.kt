package org.openscreentime.schedules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openscreentime.downtime.DowntimeStatus
import org.openscreentime.groups.AppGroup
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ScheduleRulesTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val thursday = LocalDate.of(2026, 11, 5)
    private val friday = thursday.plusDays(1)
    private val saturday = thursday.plusDays(2)
    private fun at(time: String, date: LocalDate = thursday): Instant = date.atTime(LocalTime.parse(time)).atZone(zone).toInstant()

    private val work = AppSchedule(
        id = 1,
        name = "Work",
        days = SchedulePreset.WorkSchool.days,
        window = SchedulePreset.WorkSchool.window,
        packages = setOf("video"),
        groupIds = setOf(10),
    )
    private val bedtime = AppSchedule(
        id = 2,
        name = "Bedtime",
        days = SchedulePreset.Bedtime.days,
        window = SchedulePreset.Bedtime.window,
        packages = setOf("chat"),
    )
    private val social = AppGroup(10, "Social", setOf("chat", "photos"))

    @Test
    fun presetsMatchTheAgreedTimes() {
        assertEquals(LocalTime.of(8, 0), SchedulePreset.WorkSchool.window.start)
        assertEquals(LocalTime.of(17, 0), SchedulePreset.WorkSchool.window.end)
        assertEquals(DayOfWeek.entries.toSet() - DayOfWeek.SATURDAY - DayOfWeek.SUNDAY, SchedulePreset.WorkSchool.days)
        assertEquals(LocalTime.of(21, 0), SchedulePreset.Bedtime.window.start)
        assertEquals(LocalTime.of(6, 0), SchedulePreset.Bedtime.window.end)
        assertEquals(DayOfWeek.entries.toSet(), SchedulePreset.Bedtime.days)
    }

    @Test
    fun workScheduleIsOnDuringWeekdayHoursOnly() {
        assertEquals(DowntimeStatus.ActiveUntil(thursday.atTime(17, 0).atZone(zone)), work.status(at("08:00"), zone))
        assertEquals(DowntimeStatus.StartsAt(friday.atTime(8, 0).atZone(zone)), work.status(at("17:00"), zone))
        // Friday evening: next start is Monday.
        assertEquals(DowntimeStatus.StartsAt(saturday.plusDays(2).atTime(8, 0).atZone(zone)), work.status(at("18:00", friday), zone))
    }

    @Test
    fun bedtimeRunsOvernight() {
        assertEquals(DowntimeStatus.ActiveUntil(friday.atTime(6, 0).atZone(zone)), bedtime.status(at("23:30"), zone))
        // Early Friday morning is still Thursday night's bedtime.
        assertEquals(DowntimeStatus.ActiveUntil(friday.atTime(6, 0).atZone(zone)), bedtime.status(at("05:59", friday), zone))
    }

    @Test
    fun aSwitchedOffScheduleIsOff() {
        assertEquals(DowntimeStatus.Off, work.copy(enabled = false).status(at("10:00"), zone))
        assertEquals(DowntimeStatus.Off, work.copy(days = emptySet()).status(at("10:00"), zone))
    }

    @Test
    fun blocksItsOwnAppsAndItsGroupsApps() {
        assertEquals(setOf("video", "chat", "photos"), work.blockedPackages(listOf(social)))
        assertEquals(setOf("video"), work.blockedPackages(emptyList()))
    }

    @Test
    fun activeSchedulesForAnApp() {
        val schedules = listOf(work, bedtime)
        assertEquals(
            listOf(ActiveSchedule("Work", thursday.atTime(17, 0).atZone(zone), id = 1)),
            ScheduleRules.activeFor("photos", schedules, listOf(social), at("10:00"), zone),
        )
        assertTrue(ScheduleRules.activeFor("maps", schedules, listOf(social), at("10:00"), zone).isEmpty())
        assertEquals(listOf("Bedtime"), ScheduleRules.activeFor("chat", schedules, listOf(social), at("22:00"), zone).map { it.name })
    }

    @Test
    fun daysAreStoredAsOneNumber() {
        assertEquals(0, DaysMask.fromDays(emptySet()))
        assertEquals(31, DaysMask.fromDays(SchedulePreset.WorkSchool.days))
        assertEquals(127, DaysMask.fromDays(DayOfWeek.entries.toSet()))
        val some = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY)
        assertEquals(some, DaysMask.toDays(DaysMask.fromDays(some)))
    }
}
