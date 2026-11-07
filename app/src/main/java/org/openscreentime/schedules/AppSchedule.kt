package org.openscreentime.schedules

import org.openscreentime.downtime.DowntimeSchedule
import org.openscreentime.downtime.DowntimeStatus
import org.openscreentime.downtime.DowntimeWindow
import org.openscreentime.groups.AppGroup
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Blocks chosen apps and groups at set times, for example during work
 * hours. Unlike downtime (which blocks everything but always allowed
 * apps), a schedule only blocks what it lists. [id] is 0 until saved.
 *
 * The window belongs to the day it starts on, like downtime: a Friday
 * 21:00–06:00 schedule runs from Friday night into Saturday morning.
 */
data class AppSchedule(
    val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val days: Set<DayOfWeek>,
    val window: DowntimeWindow,
    val packages: Set<String> = emptySet(),
    val groupIds: Set<Long> = emptySet(),
) {
    /** Reuses downtime's tested logic for overnight windows, clock changes and back-to-back days. */
    private val asWeekly: DowntimeSchedule
        get() = DowntimeSchedule(
            enabled = enabled && days.isNotEmpty(),
            sameEveryDay = false,
            days = DayOfWeek.entries.associateWith { if (it in days) window else null },
        )

    /** Whether the schedule is on at [at] and until when, or when it next starts. */
    fun status(at: Instant, zone: ZoneId): DowntimeStatus = asWeekly.status(at, zone)

    /** Every app this schedule blocks: its own apps plus the apps in its groups. */
    fun blockedPackages(groups: List<AppGroup>): Set<String> =
        packages + groups.filter { it.id in groupIds }.flatMap { it.packages }
}

/** Ready-made schedules the user can add and then change. */
enum class SchedulePreset(val days: Set<DayOfWeek>, val window: DowntimeWindow) {
    /** 8:00 AM to 5:00 PM, Monday to Friday. */
    WorkSchool(
        days = DayOfWeek.entries.toSet() - DayOfWeek.SATURDAY - DayOfWeek.SUNDAY,
        window = DowntimeWindow(LocalTime.of(8, 0), LocalTime.of(17, 0)),
    ),

    /** 9:00 PM to 6:00 AM, every day. */
    Bedtime(
        days = DayOfWeek.entries.toSet(),
        window = DowntimeWindow(LocalTime.of(21, 0), LocalTime.of(6, 0)),
    ),
}

/** A schedule that's on right now, for one app. */
data class ActiveSchedule(val name: String, val end: java.time.ZonedDateTime, val id: Long = 0)

object ScheduleRules {
    const val MAX_NAME_LENGTH = 40

    /** Tidies a typed name; null if nothing is left. */
    fun cleanName(name: String): String? = name.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH).ifEmpty { null }

    /** The schedules blocking [packageName] at [now], with when each ends. */
    fun activeFor(
        packageName: String,
        schedules: List<AppSchedule>,
        groups: List<AppGroup>,
        now: Instant,
        zone: ZoneId,
    ): List<ActiveSchedule> = schedules.mapNotNull { schedule ->
        if (packageName !in schedule.blockedPackages(groups)) return@mapNotNull null
        val status = schedule.status(now, zone) as? DowntimeStatus.ActiveUntil ?: return@mapNotNull null
        ActiveSchedule(schedule.name, status.end, schedule.id)
    }
}
