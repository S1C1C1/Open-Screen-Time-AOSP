package org.openscreentime.usage

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Time used per app on each day: day -> (package name -> time). */
typealias DailyTotals = Map<LocalDate, Map<String, Duration>>

/**
 * Turns a list of usage events into time used per app per local day.
 *
 * Rules:
 * - An app counts from when one of its screens comes to the front until
 *   none of its screens are at the front.
 * - Screen off, lock or shutdown ends every app's time.
 * - Time crossing local midnight is split between the two days.
 * - An app still at the front at [windowEnd] counts up to [windowEnd].
 * - If an app's first event in the window says it left the front, it was
 *   already open when the window began, so it counts from [windowStart],
 *   unless the screen turned off before that.
 * - Apps in [excludedPackages] (such as the home screen) are not counted.
 * - Two apps on screen at once (split screen) are both counted.
 */
object UsageCalculator {

    fun dailyTotals(
        events: List<UsageEvent>,
        windowStart: Instant,
        windowEnd: Instant,
        zone: ZoneId,
        excludedPackages: Set<String> = emptySet(),
    ): DailyTotals {
        require(!windowEnd.isBefore(windowStart)) { "windowEnd must not be before windowStart" }
        val totals = Totals(zone, excludedPackages)
        val open = mutableMapOf<String, OpenSession>()
        val seenPackages = mutableSetOf<String>()
        var screenTurnedOff = false

        fun clamp(time: Instant) = time.coerceIn(windowStart, windowEnd)

        // sortedBy is stable, so events with the same time keep Android's order.
        for (event in events.sortedBy { it.time }) {
            val time = clamp(event.time)
            when (event) {
                is UsageEvent.AppResumed -> {
                    val session = open.getOrPut(event.packageName) { OpenSession(time) }
                    session.activities += event.className
                    seenPackages += event.packageName
                }
                is UsageEvent.AppPaused -> {
                    val session = open[event.packageName]
                    if (session != null) {
                        session.activities -= event.className
                        if (session.activities.isEmpty()) {
                            totals.add(event.packageName, session.start, time)
                            open.remove(event.packageName)
                        }
                    } else if (event.packageName !in seenPackages && !screenTurnedOff) {
                        // Already open when the window began.
                        totals.add(event.packageName, windowStart, time)
                    }
                    seenPackages += event.packageName
                }
                is UsageEvent.ScreenOff -> {
                    open.forEach { (pkg, session) -> totals.add(pkg, session.start, time) }
                    open.clear()
                    screenTurnedOff = true
                }
            }
        }
        // Apps still in use when the window ends.
        open.forEach { (pkg, session) -> totals.add(pkg, session.start, windowEnd) }
        return totals.result()
    }

    private class OpenSession(val start: Instant) {
        val activities = mutableSetOf<String>()
    }

    /** Adds up time per day and app, splitting at local midnight. */
    private class Totals(private val zone: ZoneId, private val excluded: Set<String>) {
        private val byDay = mutableMapOf<LocalDate, MutableMap<String, Duration>>()

        fun add(packageName: String, from: Instant, to: Instant) {
            if (packageName in excluded || !to.isAfter(from)) return
            var start = from
            var day = start.atZone(zone).toLocalDate()
            while (start.isBefore(to)) {
                // atStartOfDay handles days that are 23 or 25 hours long (daylight saving).
                val nextMidnight = day.plusDays(1).atStartOfDay(zone).toInstant()
                val end = minOf(to, nextMidnight)
                val apps = byDay.getOrPut(day) { mutableMapOf() }
                apps[packageName] = (apps[packageName] ?: Duration.ZERO) + Duration.between(start, end)
                start = end
                day = day.plusDays(1)
            }
        }

        fun result(): DailyTotals = byDay
    }
}
