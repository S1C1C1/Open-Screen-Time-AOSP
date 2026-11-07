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

/**
 * Which block extra time is for. Extra time only lifts the block it was
 * given for: unlocking an app after its limit doesn't also let it through
 * downtime or a schedule later that day.
 */
object BlockKeys {
    const val LIMIT = "limit"
    const val DOWNTIME = "downtime"

    /** Extra time saved before blocks had keys; it still lifts every block until it runs out. */
    const val ANY = "*"

    fun schedule(scheduleId: Long) = "schedule:$scheduleId"

    /** The key for a block of [reason]. Schedules need their id, so this is only a fallback for them. */
    fun forReason(reason: BlockReason): String = when (reason) {
        BlockReason.LimitReached -> LIMIT
        BlockReason.Downtime -> DOWNTIME
        BlockReason.Scheduled -> ANY
    }
}

/** Extra time given to one app: by the PIN, "one more minute", or ignoring a reminder. */
data class AppOverride(
    /** For each block (see [BlockKeys]), until when the app may be used despite it. */
    val allowedUntil: Map<String, Instant> = emptyMap(),
    /** The day "one more minute" was last used for this app. */
    val graceUsedOn: LocalDate? = null,
) {
    /** Until when the block with [key] is lifted, or null. */
    fun untilFor(key: String): Instant? = listOfNotNull(allowedUntil[key], allowedUntil[BlockKeys.ANY]).maxOrNull()
}

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
        /** Which block this is, so extra time lifts only this one (see [BlockKeys]). */
        val overrideKey: String = BlockKeys.forReason(reason),
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

        // Every block that applies right now, in the order they're shown:
        // downtime first (it applies even to apps under their limit), then
        // schedules (the one ending last first), then limits.
        val blocks = buildList {
            val status = downtime.status(now, zone)
            if (status is DowntimeStatus.ActiveUntil) add(BlockDecision.Blocked(BlockReason.Downtime, status.end, graceAvailable))
            schedules.sortedByDescending { it.end.toInstant() }.forEach { schedule ->
                add(
                    BlockDecision.Blocked(
                        BlockReason.Scheduled, schedule.end, graceAvailable, schedule.name, BlockKeys.schedule(schedule.id),
                    ),
                )
            }
            // The app's own limit or any of its groups' shared limits block it until midnight.
            val midnight = today.plusDays(1).atStartOfDay(zone)
            val reachedGroup = groups.firstOrNull { it.isReached(today) }
            when {
                limit != null && limit.isReached(today, usedToday) ->
                    add(BlockDecision.Blocked(BlockReason.LimitReached, midnight, graceAvailable))
                reachedGroup != null ->
                    add(BlockDecision.Blocked(BlockReason.LimitReached, midnight, graceAvailable, reachedGroup.group.name))
            }
        }
        if (blocks.isEmpty()) return BlockDecision.Allowed

        // Extra time lifts only the block it was given for; any other block still applies.
        fun lifted(block: BlockDecision.Blocked) = override.untilFor(block.overrideKey)?.isAfter(now) == true
        blocks.firstOrNull { !lifted(it) }?.let { return it }
        // Every block is lifted: allowed until the first of those lifts runs out.
        return BlockDecision.AllowedUntil(blocks.minOf { override.untilFor(it.overrideKey)!! })
    }

    /** When a PIN unlock of [duration] ends, for an app blocked as in [blocked]. */
    fun unlockEnd(now: Instant, duration: UnlockDuration, blocked: BlockDecision.Blocked): Instant = when (duration) {
        UnlockDuration.FifteenMinutes -> now + Duration.ofMinutes(15)
        UnlockDuration.OneHour -> now + Duration.ofHours(1)
        // For a limit this is midnight; for downtime or a schedule, when it ends.
        UnlockDuration.RestOfDay -> blocked.blockedUntil.toInstant()
    }
}
