package org.openscreentime.blocking

import org.openscreentime.downtime.DowntimeSchedule
import org.openscreentime.downtime.DowntimeStatus
import org.openscreentime.groups.GroupUsage
import org.openscreentime.limits.AppLimit
import org.openscreentime.schedules.ActiveSchedule
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** What happens when an app's time is up or downtime starts. One setting for the whole phone. */
enum class LimitMode {
    /** The app stays blocked. Only the PIN gives more time. */
    Strict,

    /** "One more minute" once per app per day; after that, the PIN. */
    Grace,

    /** A warning that can be ignored for 15 minutes at a time. */
    Reminder,
    ;

    companion object {
        val Default = Strict
    }
}

enum class BlockReason { LimitReached, Downtime, Scheduled }

/** Extra time given to one app: by the PIN, "one more minute", or ignoring a reminder. */
data class AppOverride(
    /** The app may be used until this moment, whatever the limits or downtime. */
    val allowedUntil: Instant? = null,
    /** The day "one more minute" was last used for this app. */
    val graceUsedOn: LocalDate? = null,
)

sealed interface BlockDecision {
    data object Allowed : BlockDecision

    /** Allowed only because of extra time, which runs out at [until]. */
    data class AllowedUntil(val until: Instant) : BlockDecision

    /**
     * The app should be blocked. [blockedUntil] is when it would be allowed
     * again on its own: the end of downtime, or midnight for a limit.
     */
    data class Blocked(
        val reason: BlockReason,
        val blockedUntil: ZonedDateTime,
        val graceAvailable: Boolean,
        /**
         * What caused it, to show on the block screen: the app group whose
         * shared limit was reached, or the schedule that's on.
         */
        val ruleName: String? = null,
    ) : BlockDecision
}

/** How long the PIN unlocks an app for. */
enum class UnlockDuration { FifteenMinutes, OneHour, RestOfDay }

/**
 * Decides whether an app should be blocked right now. Pure logic with no
 * Android code, so the same rules work for any enforcement method (phase 2).
 */
object BlockRules {

    val ONE_MORE_MINUTE: Duration = Duration.ofMinutes(1)
    val REMINDER_SNOOZE: Duration = Duration.ofMinutes(15)

    fun decide(
        now: Instant,
        zone: ZoneId,
        usedToday: Duration,
        limit: AppLimit?,
        downtime: DowntimeSchedule,
        alwaysAllowed: Boolean,
        override: AppOverride,
        mode: LimitMode,
        groups: List<GroupUsage> = emptyList(),
        /** Schedules blocking this app right now. */
        schedules: List<ActiveSchedule> = emptyList(),
    ): BlockDecision {
        if (alwaysAllowed) return BlockDecision.Allowed

        val today = now.atZone(zone).toLocalDate()
        val graceAvailable = mode == LimitMode.Grace && override.graceUsedOn != today

        // Downtime comes first: it applies even to apps under their limit.
        // Then schedules, then limits. If several schedules are on, the app
        // stays blocked until the last of them ends.
        val schedule = schedules.maxByOrNull { it.end.toInstant() }
        val blocked = when (val status = downtime.status(now, zone)) {
            is DowntimeStatus.ActiveUntil -> BlockDecision.Blocked(BlockReason.Downtime, status.end, graceAvailable)
            else -> if (schedule != null) {
                BlockDecision.Blocked(BlockReason.Scheduled, schedule.end, graceAvailable, schedule.name)
            } else {
                // The app's own limit or any of its groups' shared limits:
                // whichever is reached first blocks it until midnight.
                val midnight = today.plusDays(1).atStartOfDay(zone)
                val reachedGroup = groups.firstOrNull { it.isReached(today) }
                when {
                    limit != null && limit.isReached(today, usedToday) ->
                        BlockDecision.Blocked(BlockReason.LimitReached, midnight, graceAvailable)
                    reachedGroup != null ->
                        BlockDecision.Blocked(BlockReason.LimitReached, midnight, graceAvailable, reachedGroup.group.name)
                    else -> null
                }
            }
        } ?: return BlockDecision.Allowed

        val until = override.allowedUntil
        return if (until != null && now.isBefore(until)) BlockDecision.AllowedUntil(until) else blocked
    }

    /** When a PIN unlock of [duration] ends, for an app blocked as in [blocked]. */
    fun unlockEnd(now: Instant, duration: UnlockDuration, blocked: BlockDecision.Blocked): Instant = when (duration) {
        UnlockDuration.FifteenMinutes -> now + Duration.ofMinutes(15)
        UnlockDuration.OneHour -> now + Duration.ofHours(1)
        // For a limit this is midnight; for downtime or a schedule, when it ends.
        UnlockDuration.RestOfDay -> blocked.blockedUntil.toInstant()
    }
}
