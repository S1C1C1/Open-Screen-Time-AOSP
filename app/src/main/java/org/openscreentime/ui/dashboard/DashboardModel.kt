package org.openscreentime.ui.dashboard

import org.openscreentime.downtime.DowntimeStatus
import org.openscreentime.groups.AppGroup
import org.openscreentime.groups.DailyLimit
import org.openscreentime.groups.GroupRules
import org.openscreentime.limits.AppLimit
import org.openscreentime.schedules.AppSchedule
import org.openscreentime.usage.AppLabels
import org.openscreentime.usage.AppUsageTime
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** One app's time, with the name shown to the user. */
data class AppTime(
    val packageName: String,
    val label: String,
    val time: Duration,
    val hasLimit: Boolean = false,
    /** A schedule is blocking this app right now. */
    val scheduledNow: Boolean = false,
)

/** One bar in a week chart. */
data class DayBar(
    val date: LocalDate,
    val time: Duration,
    val isToday: Boolean,
    /** Days after today have no bar and can't be selected. */
    val isFuture: Boolean,
)

/** Everything the dashboard shows. */
data class Dashboard(
    val week: List<DayBar>,
    val selectedDay: LocalDate,
    val today: LocalDate,
    /** Total for the selected day. */
    val total: Duration,
    /** Apps used on the selected day, most used first. */
    val apps: List<AppTime>,
) {
    val isTodaySelected: Boolean get() = selectedDay == today
}

/** The 7 days of the calendar week containing [date], starting on [firstDayOfWeek]. */
fun weekContaining(date: LocalDate, firstDayOfWeek: DayOfWeek): List<LocalDate> {
    val start = date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    return (0L until 7L).map { start.plusDays(it) }
}

private fun List<AppUsageTime>?.total(): Duration =
    orEmpty().fold(Duration.ZERO) { sum, app -> sum + app.time }

/**
 * Builds the dashboard for the week containing [today]. If [selectedDay] is
 * missing, in the future, or not in this week, today is shown instead.
 */
fun buildDashboard(
    usageByDay: Map<LocalDate, List<AppUsageTime>>,
    today: LocalDate,
    selectedDay: LocalDate?,
    firstDayOfWeek: DayOfWeek,
    labels: AppLabels,
    limitedPackages: Set<String> = emptySet(),
    /** Apps a schedule is blocking right now; only marked when today is shown. */
    scheduledNow: Set<String> = emptySet(),
): Dashboard {
    val week = weekContaining(today, firstDayOfWeek)
    val shownDay = selectedDay?.takeIf { it in week && !it.isAfter(today) } ?: today
    val shownApps = usageByDay[shownDay].orEmpty()
        .sortedWith(compareByDescending<AppUsageTime> { it.time }.thenBy { it.packageName })
    return Dashboard(
        week = week.map { day ->
            DayBar(
                date = day,
                time = if (day.isAfter(today)) Duration.ZERO else usageByDay[day].total(),
                isToday = day == today,
                isFuture = day.isAfter(today),
            )
        },
        selectedDay = shownDay,
        today = today,
        total = shownApps.total(),
        apps = shownApps.map {
            AppTime(
                packageName = it.packageName,
                label = labels.labelFor(it.packageName),
                time = it.time,
                hasLimit = it.packageName in limitedPackages,
                scheduledNow = shownDay == today && it.packageName in scheduledNow,
            )
        },
    )
}

/** What the app detail screen shows. */
data class AppDetail(
    val packageName: String,
    val label: String,
    val today: Duration,
    val week: List<DayBar>,
    /** Average per day over the days of this week so far, including today. */
    val dailyAverage: Duration,
    val limit: AppLimit? = null,
    /** Time left under today's limit, or null without a limit. */
    val timeLeftToday: Duration? = null,
    /** Groups this app is in. */
    val groups: List<GroupOnApp> = emptyList(),
    /** Schedules that block this app, directly or through one of its groups. */
    val schedules: List<ScheduleOnApp> = emptyList(),
)

/** A group an app is in, with its shared limit and what's left of it today. */
data class GroupOnApp(
    val id: Long,
    val name: String,
    val limit: DailyLimit?,
    /** Null when the group has no limit. */
    val timeLeftToday: Duration?,
)

/** A schedule that blocks an app. */
data class ScheduleOnApp(
    val schedule: AppSchedule,
    /** The group the app is blocked through, or null when the schedule lists the app itself. */
    val viaGroup: String?,
    /** When the schedule ends, if it's on right now. */
    val activeUntil: ZonedDateTime?,
)

fun buildAppDetail(
    packageName: String,
    usageByDay: Map<LocalDate, List<AppUsageTime>>,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    labels: AppLabels,
    limit: AppLimit? = null,
    groups: List<AppGroup> = emptyList(),
    schedules: List<AppSchedule> = emptyList(),
    now: Instant = Instant.EPOCH,
    zone: ZoneId = ZoneId.systemDefault(),
): AppDetail {
    fun timeOn(day: LocalDate): Duration =
        usageByDay[day]?.firstOrNull { it.packageName == packageName }?.time ?: Duration.ZERO

    val week = weekContaining(today, firstDayOfWeek).map { day ->
        DayBar(
            date = day,
            time = if (day.isAfter(today)) Duration.ZERO else timeOn(day),
            isToday = day == today,
            isFuture = day.isAfter(today),
        )
    }
    val daysSoFar = week.filterNot { it.isFuture }
    val totalSoFar = daysSoFar.fold(Duration.ZERO) { sum, bar -> sum + bar.time }
    return AppDetail(
        packageName = packageName,
        label = labels.labelFor(packageName),
        today = timeOn(today),
        week = week,
        // daysSoFar always includes today, so this never divides by zero.
        dailyAverage = totalSoFar.dividedBy(daysSoFar.size.toLong()),
        limit = limit,
        timeLeftToday = limit?.timeLeft(today, timeOn(today)),
        groups = groupsOn(packageName, groups, usageByDay[today].orEmpty(), today),
        schedules = schedulesOn(packageName, groups, schedules, now, zone),
    )
}

private fun groupsOn(packageName: String, groups: List<AppGroup>, usageToday: List<AppUsageTime>, today: LocalDate): List<GroupOnApp> {
    val used = usageToday.associate { it.packageName to it.time }
    return groups.filter { packageName in it.packages }.map { group ->
        val left = group.limit?.let { limit ->
            (limit.limitOn(today) - GroupRules.usedToday(group, used)).coerceAtLeast(Duration.ZERO)
        }
        GroupOnApp(group.id, group.name, group.limit, left)
    }
}

private fun schedulesOn(
    packageName: String,
    groups: List<AppGroup>,
    schedules: List<AppSchedule>,
    now: Instant,
    zone: ZoneId,
): List<ScheduleOnApp> = schedules.mapNotNull { schedule ->
    val viaGroup = groups.firstOrNull { it.id in schedule.groupIds && packageName in it.packages }?.name
    if (packageName !in schedule.packages && viaGroup == null) return@mapNotNull null
    val active = schedule.status(now, zone) as? DowntimeStatus.ActiveUntil
    ScheduleOnApp(schedule, viaGroup.takeIf { packageName !in schedule.packages }, active?.end)
}
