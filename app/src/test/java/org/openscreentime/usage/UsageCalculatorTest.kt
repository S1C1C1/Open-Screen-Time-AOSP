package org.openscreentime.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openscreentime.usage.UsageEvent.AppPaused
import org.openscreentime.usage.UsageEvent.AppResumed
import org.openscreentime.usage.UsageEvent.ScreenOff
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class UsageCalculatorTest {

    // A zone with daylight saving, to catch 23- and 25-hour days.
    private val zone = ZoneId.of("Europe/Berlin")
    private val day = LocalDate.of(2026, 11, 5)

    private fun at(time: String, date: LocalDate = day): Instant =
        LocalDateTime.of(date, java.time.LocalTime.parse(time)).atZone(zone).toInstant()

    private fun resumed(time: String, pkg: String, cls: String = "Main", date: LocalDate = day) =
        AppResumed(at(time, date), pkg, cls)

    private fun paused(time: String, pkg: String, cls: String = "Main", date: LocalDate = day) =
        AppPaused(at(time, date), pkg, cls)

    private fun calculate(
        events: List<UsageEvent>,
        start: Instant = at("00:00"),
        end: Instant = at("23:59"),
        excluded: Set<String> = emptySet(),
    ) = UsageCalculator.dailyTotals(events, start, end, zone, excluded)

    private fun minutes(n: Long) = Duration.ofMinutes(n)

    @Test
    fun noEventsMeansNoUsage() {
        assertTrue(calculate(emptyList()).isEmpty())
    }

    @Test
    fun singleSession() {
        val totals = calculate(listOf(resumed("10:00", "browser"), paused("10:30", "browser")))
        assertEquals(mapOf(day to mapOf("browser" to minutes(30))), totals)
    }

    @Test
    fun sessionsOfTheSameAppAddUp() {
        val totals = calculate(
            listOf(
                resumed("10:00", "browser"), paused("10:10", "browser"),
                resumed("12:00", "browser"), paused("12:05", "browser"),
            ),
        )
        assertEquals(minutes(15), totals[day]!!["browser"])
    }

    @Test
    fun switchingBetweenApps() {
        val totals = calculate(
            listOf(
                resumed("09:00", "mail"),
                paused("09:20", "mail"),
                resumed("09:20", "chat"),
                paused("09:45", "chat"),
            ),
        )
        assertEquals(mapOf("mail" to minutes(20), "chat" to minutes(25)), totals[day])
    }

    @Test
    fun movingBetweenScreensInsideOneAppIsOneSession() {
        // Opening a second screen in the same app; the first pauses after the second resumes.
        val totals = calculate(
            listOf(
                resumed("08:00", "mail", "Inbox"),
                resumed("08:10", "mail", "Message"),
                paused("08:10", "mail", "Inbox"),
                paused("08:30", "mail", "Message"),
            ),
        )
        assertEquals(minutes(30), totals[day]!!["mail"])
    }

    @Test
    fun stoppedAfterPausedIsNotCountedTwice() {
        val totals = calculate(
            listOf(
                resumed("10:00", "maps"),
                paused("10:20", "maps"),
                // Android also reports "stopped" for the same screen.
                paused("10:21", "maps"),
            ),
        )
        assertEquals(minutes(20), totals[day]!!["maps"])
    }

    @Test
    fun screenOffEndsEverySession() {
        val totals = calculate(
            listOf(
                resumed("22:00", "video"),
                ScreenOff(at("22:40")),
                // A late pause after the screen went off doesn't add time.
                paused("22:41", "video"),
            ),
        )
        assertEquals(minutes(40), totals[day]!!["video"])
    }

    @Test
    fun appStillOpenCountsUntilNow() {
        val totals = calculate(listOf(resumed("15:00", "game")), end = at("15:25"))
        assertEquals(minutes(25), totals[day]!!["game"])
    }

    @Test
    fun appAlreadyOpenWhenWindowStartsCountsFromStart() {
        // The window starts at midnight; the app was opened the evening before.
        val totals = calculate(listOf(paused("00:20", "reader")))
        assertEquals(minutes(20), totals[day]!!["reader"])
    }

    @Test
    fun strayPauseAfterScreenOffIsIgnored() {
        val totals = calculate(listOf(ScreenOff(at("00:30")), paused("08:00", "reader")))
        assertTrue(totals.isEmpty())
    }

    @Test
    fun splitScreenCountsBothApps() {
        val totals = calculate(
            listOf(
                resumed("13:00", "video"),
                resumed("13:00", "notes"),
                paused("13:30", "notes"),
                paused("13:45", "video"),
            ),
        )
        assertEquals(mapOf("video" to minutes(45), "notes" to minutes(30)), totals[day])
    }

    @Test
    fun homeScreenIsNotCounted() {
        val totals = calculate(
            listOf(
                resumed("10:00", "launcher"),
                paused("10:05", "launcher"),
                resumed("10:05", "camera"),
                paused("10:15", "camera"),
            ),
            excluded = setOf("launcher"),
        )
        assertEquals(mapOf(day to mapOf("camera" to minutes(10))), totals)
    }

    @Test
    fun useAcrossMidnightIsSplitBetweenDays() {
        val nextDay = day.plusDays(1)
        val totals = calculate(
            listOf(
                resumed("23:30", "video"),
                paused("00:45", "video", date = nextDay),
            ),
            end = at("12:00", nextDay),
        )
        assertEquals(minutes(30), totals[day]!!["video"])
        assertEquals(minutes(45), totals[nextDay]!!["video"])
    }

    @Test
    fun useAcrossSeveralDaysIsSplitIntoEachDay() {
        val totals = calculate(
            listOf(resumed("23:00", "kiosk"), paused("01:00", "kiosk", date = day.plusDays(2))),
            end = at("12:00", day.plusDays(2)),
        )
        assertEquals(minutes(60), totals[day]!!["kiosk"])
        assertEquals(Duration.ofHours(24), totals[day.plusDays(1)]!!["kiosk"])
        assertEquals(minutes(60), totals[day.plusDays(2)]!!["kiosk"])
    }

    @Test
    fun clocksGoingBackMakesA25HourDay() {
        // 25 October 2026: clocks in Berlin go back from 03:00 to 02:00.
        val dstEnd = LocalDate.of(2026, 10, 25)
        val start = dstEnd.atStartOfDay(zone).toInstant()
        val end = dstEnd.plusDays(1).atStartOfDay(zone).toInstant()
        val totals = UsageCalculator.dailyTotals(
            listOf(AppResumed(start, "player", "Main")),
            start,
            end,
            zone,
        )
        assertEquals(Duration.ofHours(25), totals[dstEnd]!!["player"])
    }

    @Test
    fun clocksGoingForwardMakesA23HourDay() {
        // 29 March 2026: clocks in Berlin skip from 02:00 to 03:00.
        val dstStart = LocalDate.of(2026, 3, 29)
        val start = dstStart.atStartOfDay(zone).toInstant()
        val end = dstStart.plusDays(1).atStartOfDay(zone).toInstant()
        val totals = UsageCalculator.dailyTotals(
            listOf(AppResumed(start, "player", "Main")),
            start,
            end,
            zone,
        )
        assertEquals(Duration.ofHours(23), totals[dstStart]!!["player"])
    }

    @Test
    fun eventsOutOfOrderAreSortedFirst() {
        val totals = calculate(listOf(paused("10:30", "browser"), resumed("10:00", "browser")))
        assertEquals(minutes(30), totals[day]!!["browser"])
    }

    @Test
    fun eventsOutsideTheWindowAreClipped() {
        val totals = calculate(
            listOf(resumed("09:00", "news"), paused("11:00", "news")),
            start = at("10:00"),
            end = at("10:30"),
        )
        assertEquals(minutes(30), totals[day]!!["news"])
    }
}
