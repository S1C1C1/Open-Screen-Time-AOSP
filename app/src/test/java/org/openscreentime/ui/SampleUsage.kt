package org.openscreentime.ui

import org.openscreentime.ui.dashboard.Dashboard
import org.openscreentime.ui.dashboard.buildDashboard
import org.openscreentime.usage.AppUsageTime
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate

/** Fixed sample data so screenshots never change from run to run. */
object SampleUsage {
    // Thursday 5 November 2026, in a week starting on Sunday.
    val today: LocalDate = LocalDate.of(2026, 11, 5)
    val firstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY

    private val names = mapOf(
        "browser" to "Browser",
        "messages" to "Messages",
        "longname" to "A very long app name that does not fit on one line",
        "camera" to "Camera",
        "clock" to "Clock",
        "maps" to "Maps",
        "video" to "Video",
    )
    val labels = { pkg: String -> names[pkg] ?: pkg }

    private fun app(pkg: String, minutes: Long, seconds: Long = 0) =
        AppUsageTime(pkg, Duration.ofMinutes(minutes).plusSeconds(seconds))

    val usageByDay: Map<LocalDate, List<AppUsageTime>> = mapOf(
        today.minusDays(4) to listOf(app("video", 95), app("browser", 40)),
        today.minusDays(3) to listOf(app("browser", 70), app("maps", 25)),
        // Tuesday: no use recorded.
        today.minusDays(1) to listOf(app("video", 150), app("messages", 32), app("browser", 18)),
        today to listOf(
            app("browser", 74),
            app("messages", 48),
            app("longname", 31),
            app("camera", 12),
            app("maps", 9),
            app("clock", 0, 40),
        ),
    )

    fun dashboard(selectedDay: LocalDate? = null, usage: Map<LocalDate, List<AppUsageTime>> = usageByDay): Dashboard =
        buildDashboard(usage, today, selectedDay, firstDayOfWeek, labels)
}
