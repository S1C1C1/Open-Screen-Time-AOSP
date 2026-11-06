package org.openscreentime.limits

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate

/**
 * A daily time limit for one app. [weekend] is null when the same limit
 * applies every day.
 */
data class AppLimit(
    val packageName: String,
    val weekday: Duration,
    val weekend: Duration? = null,
) {
    /** The limit that applies on [date]. */
    fun limitOn(date: LocalDate): Duration =
        if (weekend != null && date.dayOfWeek in LimitRules.WEEKEND) weekend else weekday

    /** Time still allowed on [date] after [used], never below zero. */
    fun timeLeft(date: LocalDate, used: Duration): Duration =
        (limitOn(date) - used).coerceAtLeast(Duration.ZERO)

    fun isReached(date: LocalDate, used: Duration): Boolean = used >= limitOn(date)
}

/** Rules for limit lengths, from the spec. */
object LimitRules {
    val STEP: Duration = Duration.ofMinutes(15)
    val MIN: Duration = STEP
    val MAX: Duration = Duration.ofHours(23).plusMinutes(45)

    /** A sensible starting point when a new limit is created. */
    val DEFAULT: Duration = Duration.ofHours(1)

    /** Saturday and Sunday for now; choosing other weekend days may come later. */
    val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    fun isValid(limit: Duration): Boolean =
        limit >= MIN && limit <= MAX && limit.toMinutes() % STEP.toMinutes() == 0L &&
            limit.toSeconds() % 60 == 0L

    fun isValid(limit: AppLimit): Boolean =
        isValid(limit.weekday) && (limit.weekend == null || isValid(limit.weekend))

    /** Brings any duration onto the 15-minute grid within the allowed range. */
    fun snap(limit: Duration): Duration {
        val steps = (limit.toMinutes() + STEP.toMinutes() / 2) / STEP.toMinutes()
        return STEP.multipliedBy(steps).coerceIn(MIN, MAX)
    }
}
