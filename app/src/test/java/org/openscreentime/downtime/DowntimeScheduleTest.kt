package org.openscreentime.downtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DowntimeScheduleTest {

    // Daylight saving zone, to cover 23- and 25-hour days.
    private val zone = ZoneId.of("Europe/Berlin")
    private val thursday = LocalDate.of(2026, 11, 5)

    private fun at(date: LocalDate, time: String): Instant =
        date.atTime(LocalTime.parse(time)).atZone(zone).toInstant()

    private fun zoned(date: LocalDate, time: String): ZonedDateTime =
        date.atTime(LocalTime.parse(time)).atZone(zone)

    private fun window(start: String, end: String) = DowntimeWindow(LocalTime.parse(start), LocalTime.parse(end))

    private fun everyDay(start: String, end: String) =
        DowntimeSchedule(enabled = true).withEveryDay(window(start, end))

    @Test
    fun overnightWindowEveryDay() {
        val schedule = everyDay("21:00", "07:00")
        assertFalse(schedule.isActive(at(thursday, "20:59"), zone))
        assertTrue(schedule.isActive(at(thursday, "21:00"), zone)) // start is included
        assertTrue(schedule.isActive(at(thursday, "23:59"), zone))
        assertTrue(schedule.isActive(at(thursday.plusDays(1), "03:00"), zone))
        assertTrue(schedule.isActive(at(thursday.plusDays(1), "06:59"), zone))
        assertFalse(schedule.isActive(at(thursday.plusDays(1), "07:00"), zone)) // end is not
        assertFalse(schedule.isActive(at(thursday, "12:00"), zone))
    }

    @Test
    fun daytimeWindow() {
        val schedule = everyDay("13:00", "15:30")
        assertFalse(schedule.isActive(at(thursday, "12:59"), zone))
        assertTrue(schedule.isActive(at(thursday, "14:00"), zone))
        assertFalse(schedule.isActive(at(thursday, "15:30"), zone))
    }

    @Test
    fun turnedOffMeansNeverActive() {
        val schedule = everyDay("00:00", "23:59").copy(enabled = false)
        assertFalse(schedule.isActive(at(thursday, "12:00"), zone))
        assertEquals(DowntimeStatus.Off, schedule.status(at(thursday, "12:00"), zone))
    }

    @Test
    fun overnightWindowBelongsToTheDayItStarts() {
        // Friday night has downtime; Saturday has none.
        val schedule = DowntimeSchedule(enabled = true, sameEveryDay = false, days = DayOfWeek.entries.associateWith { null })
            .withDay(DayOfWeek.FRIDAY, window("22:00", "08:00"))
        val friday = thursday.plusDays(1)
        val saturday = thursday.plusDays(2)

        assertTrue(schedule.isActive(at(friday, "23:00"), zone))
        assertTrue(schedule.isActive(at(saturday, "07:30"), zone)) // Friday's window spills over
        assertFalse(schedule.isActive(at(saturday, "23:00"), zone)) // Saturday itself has none
        assertFalse(schedule.isActive(at(friday, "07:30"), zone)) // Thursday has none to spill over
    }

    @Test
    fun differentWindowsPerDay() {
        val schedule = everyDay("21:00", "07:00")
            .copy(sameEveryDay = false)
            .withDay(DayOfWeek.SATURDAY, window("23:00", "09:00"))
        val saturday = thursday.plusDays(2)
        assertFalse(schedule.isActive(at(saturday, "22:00"), zone))
        assertTrue(schedule.isActive(at(saturday, "23:30"), zone))
        assertTrue(schedule.isActive(at(saturday.plusDays(1), "08:30"), zone))
    }

    @Test
    fun statusWhileActiveGivesTheEnd() {
        val schedule = everyDay("21:00", "07:00")
        assertEquals(
            DowntimeStatus.ActiveUntil(zoned(thursday.plusDays(1), "07:00")),
            schedule.status(at(thursday, "22:00"), zone),
        )
    }

    @Test
    fun statusBeforeStartGivesTheNextStart() {
        val schedule = everyDay("21:00", "07:00")
        assertEquals(DowntimeStatus.StartsAt(zoned(thursday, "21:00")), schedule.status(at(thursday, "12:00"), zone))
    }

    @Test
    fun nextStartSkipsDaysWithoutDowntime() {
        val schedule = DowntimeSchedule(enabled = true, sameEveryDay = false, days = DayOfWeek.entries.associateWith { null })
            .withDay(DayOfWeek.SATURDAY, window("23:00", "09:00"))
        assertEquals(
            DowntimeStatus.StartsAt(zoned(thursday.plusDays(2), "23:00")),
            schedule.status(at(thursday, "12:00"), zone),
        )
    }

    @Test
    fun noDaysWithDowntimeIsOff() {
        val schedule = DowntimeSchedule(enabled = true, days = DayOfWeek.entries.associateWith { null })
        assertEquals(DowntimeStatus.Off, schedule.status(at(thursday, "12:00"), zone))
    }

    @Test
    fun touchingWindowsCountAsOneStretch() {
        // Thursday 20:00–08:00 and Friday 08:00–10:00 (a morning window) join up.
        val schedule = DowntimeSchedule(enabled = true, sameEveryDay = false, days = DayOfWeek.entries.associateWith { null })
            .withDay(DayOfWeek.THURSDAY, window("20:00", "08:00"))
            .withDay(DayOfWeek.FRIDAY, window("08:00", "10:00"))
        assertEquals(
            DowntimeStatus.ActiveUntil(zoned(thursday.plusDays(1), "10:00")),
            schedule.status(at(thursday, "22:00"), zone),
        )
    }

    @Test
    fun clocksGoingBackMakeTheNightAnHourLonger() {
        // 25 October 2026: in Berlin, 03:00 becomes 02:00 again.
        val saturday = LocalDate.of(2026, 10, 24)
        val schedule = everyDay("22:00", "07:00")
        val start = zoned(saturday, "22:00")
        val status = schedule.status(start.toInstant(), zone) as DowntimeStatus.ActiveUntil
        assertEquals(10L, java.time.Duration.between(start, status.end).toHours())
    }

    @Test
    fun clocksGoingForwardMakeTheNightAnHourShorter() {
        // 29 March 2026: in Berlin, 02:00 jumps to 03:00.
        val saturday = LocalDate.of(2026, 3, 28)
        val schedule = everyDay("22:00", "07:00")
        val start = zoned(saturday, "22:00")
        val status = schedule.status(start.toInstant(), zone) as DowntimeStatus.ActiveUntil
        assertEquals(8L, java.time.Duration.between(start, status.end).toHours())
    }

    @Test
    fun startTimeThatDoesNotExistMovesToTheNextValidTime() {
        // Downtime starting at 02:30 on the night clocks skip from 02:00 to 03:00.
        val sunday = LocalDate.of(2026, 3, 29)
        val schedule = everyDay("02:30", "06:00")
        assertTrue(schedule.isActive(zoned(sunday, "03:30").toInstant(), zone))
        assertFalse(schedule.isActive(zoned(sunday, "01:59").toInstant(), zone))
    }

    @Test(expected = IllegalArgumentException::class)
    fun startAndEndCannotBeTheSame() {
        window("21:00", "21:00")
    }

    @Test
    fun defaultIsNinePmToSevenAmEveryDayAndOff() {
        val schedule = DowntimeSchedule()
        assertFalse(schedule.enabled)
        assertTrue(schedule.sameEveryDay)
        DayOfWeek.entries.forEach { assertEquals(window("21:00", "07:00"), schedule.windowFor(it)) }
    }
}
