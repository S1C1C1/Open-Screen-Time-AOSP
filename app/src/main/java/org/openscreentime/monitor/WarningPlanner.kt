package org.openscreentime.monitor

import org.openscreentime.downtime.DowntimeSchedule
import org.openscreentime.downtime.DowntimeStatus
import org.openscreentime.groups.AppGroup
import org.openscreentime.groups.GroupRules
import org.openscreentime.limits.AppLimit
import org.openscreentime.schedules.AppSchedule
import org.openscreentime.usage.AppUsageTime
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** A heads-up shown as a notification before time runs out. */
sealed interface Warning {
    /** A key that identifies this warning, so it's only shown once. */
    val key: String

    data class LimitSoon(val packageName: String, val left: Duration, val day: Long) : Warning {
        override val key get() = "limit:$packageName:$day"
    }

    data class GroupLimitSoon(val groupId: Long, val groupName: String, val left: Duration, val day: Long) : Warning {
        override val key get() = "group:$groupId:$day"
    }

    data class ScheduleSoon(val scheduleId: Long, val name: String, val start: ZonedDateTime) : Warning {
        override val key get() = "schedule:$scheduleId:${start.toEpochSecond()}"
    }

    data class DowntimeSoon(val start: ZonedDateTime) : Warning {
        override val key get() = "downtime:${start.toEpochSecond()}"
    }
}

/**
 * Decides which "5 minutes left" warnings are due. Pure logic: the
 * background service calls it every few seconds with fresh numbers.
 */
object WarningPlanner {

    val WARN_BEFORE: Duration = Duration.ofMinutes(5)

    fun due(
        now: Instant,
        zone: ZoneId,
        usageToday: List<AppUsageTime>,
        limits: List<AppLimit>,
        downtime: DowntimeSchedule,
        isAlwaysAllowed: (String) -> Boolean,
        alreadyShown: Set<String>,
        groups: List<AppGroup> = emptyList(),
        schedules: List<AppSchedule> = emptyList(),
    ): List<Warning> {
        val today = now.atZone(zone).toLocalDate()
        val used = usageToday.associate { it.packageName to it.time }
        val warnings = mutableListOf<Warning>()

        // One warning per app per day, once 5 minutes or less are left. Not
        // once the limit is reached: the block screen takes over then.
        for (limit in limits) {
            if (isAlwaysAllowed(limit.packageName)) continue
            val left = limit.timeLeft(today, used[limit.packageName] ?: Duration.ZERO)
            if (!left.isZero && left <= WARN_BEFORE) {
                warnings += Warning.LimitSoon(limit.packageName, left, today.toEpochDay())
            }
        }

        // The same for each group's shared total. Skipped when every app in
        // the group is always allowed, since nothing would be blocked.
        for (group in groups) {
            val limit = group.limit ?: continue
            if (group.packages.all(isAlwaysAllowed)) continue
            val left = limit.limitOn(today) - GroupRules.usedToday(group, used)
            if (!left.isNegative && !left.isZero && left <= WARN_BEFORE) {
                warnings += Warning.GroupLimitSoon(group.id, group.name, left, today.toEpochDay())
            }
        }

        // One warning for each downtime start, within 5 minutes of it.
        val status = downtime.status(now, zone)
        if (status is DowntimeStatus.StartsAt) {
            val untilStart = Duration.between(now, status.start.toInstant())
            if (untilStart <= WARN_BEFORE) warnings += Warning.DowntimeSoon(status.start)
        }

        // One warning for each schedule start, within 5 minutes of it.
        // Skipped when the schedule would block nothing.
        for (schedule in schedules) {
            if (schedule.blockedPackages(groups).all(isAlwaysAllowed)) continue
            val starts = schedule.status(now, zone) as? DowntimeStatus.StartsAt ?: continue
            if (Duration.between(now, starts.start.toInstant()) <= WARN_BEFORE) {
                warnings += Warning.ScheduleSoon(schedule.id, schedule.name, starts.start)
            }
        }

        return warnings.filter { it.key !in alreadyShown }
    }

    /** Whether the background service has anything to watch. */
    fun hasSomethingToWatch(
        trackingEnabled: Boolean,
        limits: List<AppLimit>,
        downtime: DowntimeSchedule,
        groups: List<AppGroup> = emptyList(),
        schedules: List<AppSchedule> = emptyList(),
    ): Boolean = trackingEnabled &&
        (limits.isNotEmpty() || downtime.enabled || groups.any { it.limit != null } || schedules.any { it.enabled })
}
