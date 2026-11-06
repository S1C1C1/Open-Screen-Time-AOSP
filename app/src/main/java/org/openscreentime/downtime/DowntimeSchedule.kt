package org.openscreentime.downtime

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Downtime on one day, from [start] to [end]. If [end] is earlier than
 * [start], it runs overnight and ends the next morning. The window belongs to
 * the day it starts: Friday 21:00–07:00 covers Friday night into Saturday.
 */
data class DowntimeWindow(val start: LocalTime, val end: LocalTime) {
    init {
        require(start != end) { "Downtime start and end must differ" }
    }

    val crossesMidnight: Boolean get() = end < start

    /** When this window starts and ends if it begins on [date]. */
    fun on(date: LocalDate, zone: ZoneId): Interval {
        // atZone moves a time that doesn't exist (clocks going forward) to the next valid time.
        val from = date.atTime(start).atZone(zone)
        val to = (if (crossesMidnight) date.plusDays(1) else date).atTime(end).atZone(zone)
        return Interval(from, to)
    }

    companion object {
        /** The spec's example: 9:00 PM to 7:00 AM. */
        val DEFAULT = DowntimeWindow(LocalTime.of(21, 0), LocalTime.of(7, 0))
    }
}

/** A stretch of time; [start] included, [end] not. */
data class Interval(val start: ZonedDateTime, val end: ZonedDateTime) {
    operator fun contains(instant: Instant): Boolean =
        !instant.isBefore(start.toInstant()) && instant.isBefore(end.toInstant())
}

/** Where downtime stands right now. */
sealed interface DowntimeStatus {
    /** Downtime is turned off, or no day has a window. */
    data object Off : DowntimeStatus

    data class ActiveUntil(val end: ZonedDateTime) : DowntimeStatus

    data class StartsAt(val start: ZonedDateTime) : DowntimeStatus
}

/**
 * The weekly downtime schedule. Each day has its own window, or none. With
 * [sameEveryDay], the screen edits one window that applies to all days.
 */
data class DowntimeSchedule(
    val enabled: Boolean = false,
    val sameEveryDay: Boolean = true,
    val days: Map<DayOfWeek, DowntimeWindow?> = DayOfWeek.entries.associateWith { DowntimeWindow.DEFAULT },
) {
    fun windowFor(day: DayOfWeek): DowntimeWindow? = days[day]

    /** The same window on every day; used when switching to "every day". */
    fun withEveryDay(window: DowntimeWindow) =
        copy(sameEveryDay = true, days = DayOfWeek.entries.associateWith { window })

    fun withDay(day: DayOfWeek, window: DowntimeWindow?) = copy(days = days + (day to window))

    fun isActive(at: Instant, zone: ZoneId): Boolean = status(at, zone) is DowntimeStatus.ActiveUntil

    /**
     * Whether downtime is on at [at], and until when; or when it next starts.
     * Back-to-back or overlapping windows count as one stretch.
     */
    fun status(at: Instant, zone: ZoneId): DowntimeStatus {
        if (!enabled) return DowntimeStatus.Off
        val today = at.atZone(zone).toLocalDate()
        // Yesterday's window may still be running; look a week ahead for the next start.
        val intervals = (-1L..8L)
            .mapNotNull { offset ->
                val date = today.plusDays(offset)
                days[date.dayOfWeek]?.on(date, zone)
            }
            .sortedBy { it.start.toInstant() }
        if (intervals.isEmpty()) return DowntimeStatus.Off

        val merged = mergeTouching(intervals)
        merged.firstOrNull { at in it }?.let { return DowntimeStatus.ActiveUntil(it.end) }
        return merged.firstOrNull { it.start.toInstant().isAfter(at) }
            ?.let { DowntimeStatus.StartsAt(it.start) }
            ?: DowntimeStatus.Off
    }

    private fun mergeTouching(sorted: List<Interval>): List<Interval> {
        val result = mutableListOf<Interval>()
        for (interval in sorted) {
            val last = result.lastOrNull()
            if (last != null && !interval.start.isAfter(last.end)) {
                if (interval.end.isAfter(last.end)) result[result.lastIndex] = last.copy(end = interval.end)
            } else {
                result += interval
            }
        }
        return result
    }
}
