package org.openscreentime.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openscreentime.ui.components.chartScaleMax
import org.openscreentime.usage.AppUsageTime
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate

class DashboardModelTest {

    // Thursday 5 November 2026.
    private val today = LocalDate.of(2026, 11, 5)
    private val labels = { pkg: String -> pkg.replaceFirstChar { it.uppercase() } }

    private fun app(pkg: String, minutes: Long) = AppUsageTime(pkg, Duration.ofMinutes(minutes))
    private fun minutes(n: Long) = Duration.ofMinutes(n)

    @Test
    fun weekStartsOnTheRegionsFirstDay() {
        val sundayWeek = weekContaining(today, DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 11, 1), sundayWeek.first())
        assertEquals(LocalDate.of(2026, 11, 7), sundayWeek.last())

        val mondayWeek = weekContaining(today, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 11, 2), mondayWeek.first())
        assertEquals(LocalDate.of(2026, 11, 8), mondayWeek.last())
    }

    @Test
    fun weekStartingTodayBeginsWithToday() {
        val week = weekContaining(today, DayOfWeek.THURSDAY)
        assertEquals(today, week.first())
        assertEquals(7, week.size)
    }

    @Test
    fun weekCanCrossMonthAndYear() {
        val week = weekContaining(LocalDate.of(2027, 1, 1), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 12, 28), week.first())
        assertEquals(LocalDate.of(2027, 1, 3), week.last())
    }

    @Test
    fun dashboardShowsTodayByDefault() {
        val usage = mapOf(
            today to listOf(app("mail", 20), app("chat", 45)),
            today.minusDays(1) to listOf(app("video", 90)),
        )
        val dashboard = buildDashboard(usage, today, null, DayOfWeek.SUNDAY, labels)

        assertEquals(today, dashboard.selectedDay)
        assertTrue(dashboard.isTodaySelected)
        assertEquals(minutes(65), dashboard.total)
        assertEquals(
            listOf(AppTime("chat", "Chat", minutes(45)), AppTime("mail", "Mail", minutes(20))),
            dashboard.apps,
        )
    }

    @Test
    fun weekBarsHaveDailyTotalsAndMarkTodayAndFutureDays() {
        val usage = mapOf(
            today to listOf(app("mail", 20), app("chat", 45)),
            today.minusDays(1) to listOf(app("video", 90)),
        )
        val bars = buildDashboard(usage, today, null, DayOfWeek.SUNDAY, labels).week

        assertEquals(7, bars.size)
        // Sun, Mon, Tue: no use recorded.
        assertEquals(listOf(Duration.ZERO, Duration.ZERO, Duration.ZERO), bars.take(3).map { it.time })
        assertEquals(minutes(90), bars[3].time) // Wednesday
        assertEquals(minutes(65), bars[4].time) // Thursday, today
        assertTrue(bars[4].isToday)
        assertEquals(listOf(true, true), bars.drop(5).map { it.isFuture }) // Friday, Saturday
        assertFalse(bars[4].isFuture)
    }

    @Test
    fun selectingAnEarlierDayShowsThatDay() {
        val yesterday = today.minusDays(1)
        val usage = mapOf(yesterday to listOf(app("video", 90)))
        val dashboard = buildDashboard(usage, today, yesterday, DayOfWeek.SUNDAY, labels)

        assertEquals(yesterday, dashboard.selectedDay)
        assertFalse(dashboard.isTodaySelected)
        assertEquals(minutes(90), dashboard.total)
        assertEquals(listOf("Video"), dashboard.apps.map { it.label })
    }

    @Test
    fun futureOrOutOfWeekSelectionFallsBackToToday() {
        val future = buildDashboard(emptyMap(), today, today.plusDays(1), DayOfWeek.SUNDAY, labels)
        assertEquals(today, future.selectedDay)

        val lastWeek = buildDashboard(emptyMap(), today, today.minusDays(10), DayOfWeek.SUNDAY, labels)
        assertEquals(today, lastWeek.selectedDay)
    }

    @Test
    fun emptyDay() {
        val dashboard = buildDashboard(emptyMap(), today, null, DayOfWeek.SUNDAY, labels)
        assertEquals(Duration.ZERO, dashboard.total)
        assertTrue(dashboard.apps.isEmpty())
    }

    @Test
    fun appDetailShowsTodayWeekAndAverageSoFar() {
        val usage = mapOf(
            today to listOf(app("chat", 30), app("mail", 10)),
            today.minusDays(1) to listOf(app("chat", 60)),
            today.minusDays(3) to listOf(app("mail", 100)), // another app only
        )
        // Week starts Sunday: Sun..Thu so far = 5 days, chat total 90 min.
        val detail = buildAppDetail("chat", usage, today, DayOfWeek.SUNDAY, labels)

        assertEquals("Chat", detail.label)
        assertEquals(minutes(30), detail.today)
        assertEquals(minutes(18), detail.dailyAverage)
        assertEquals(
            listOf(0L, 0L, 0L, 60L, 30L, 0L, 0L),
            detail.week.map { it.time.toMinutes() },
        )
        assertEquals(listOf(false, false, false, false, false, true, true), detail.week.map { it.isFuture })
    }

    @Test
    fun appDetailOnFirstDayOfWeekAveragesTodayOnly() {
        val detail = buildAppDetail(
            "chat",
            mapOf(today to listOf(app("chat", 42))),
            today,
            DayOfWeek.THURSDAY,
            labels,
        )
        assertEquals(minutes(42), detail.dailyAverage)
    }

    @Test
    fun appNotUsedThisWeek() {
        val detail = buildAppDetail("unused", emptyMap(), today, DayOfWeek.SUNDAY, labels)
        assertEquals(Duration.ZERO, detail.today)
        assertEquals(Duration.ZERO, detail.dailyAverage)
    }

    @Test
    fun chartScaleRoundsUpToWholeHours() {
        assertEquals(Duration.ofHours(1), chartScaleMax(Duration.ZERO))
        assertEquals(Duration.ofHours(1), chartScaleMax(minutes(59)))
        assertEquals(Duration.ofHours(1), chartScaleMax(minutes(60)))
        assertEquals(Duration.ofHours(2), chartScaleMax(minutes(61)))
        assertEquals(Duration.ofHours(4), chartScaleMax(minutes(197)))
    }
}
