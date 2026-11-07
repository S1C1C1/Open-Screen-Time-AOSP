package org.openscreentime.blocking

import org.openscreentime.downtime.DowntimeSchedule
import org.openscreentime.downtime.DowntimeStatus
import org.openscreentime.groups.AppGroup
import org.openscreentime.groups.GroupRules
import org.openscreentime.limits.AppLimit
import org.openscreentime.schedules.AppSchedule
import org.openscreentime.schedules.ScheduleRules
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** An app to show the block screen for, because it's open but shouldn't be. */
data class BlockRequest(val packageName: String, val reason: BlockReason, val ruleName: String? = null)

/** What enforcement should do right now. */
data class EnforcementPlan(
    /** Apps to pause. Every other app is unpaused. */
    val pause: Set<String>,
    /** Show the block screen over this app, if it's the one open. */
    val showBlockScreen: BlockRequest?,
    /** When to check again, so limits and downtime start on time. */
    val checkAgainIn: Duration,
)

/**
 * Turns the block rules into actions for the whole phone. Pure logic, so
 * it's tested without a phone and works for any enforcement method.
 */
object EnforcementPlanner {

    val MIN_WAIT: Duration = Duration.ofSeconds(5)
    val MAX_WAIT: Duration = Duration.ofSeconds(30)

    @Suppress("LongParameterList")
    fun plan(
        now: Instant,
        zone: ZoneId,
        candidates: Set<String>,
        usageToday: Map<String, Duration>,
        limits: Map<String, AppLimit>,
        downtime: DowntimeSchedule,
        isAlwaysAllowed: (String) -> Boolean,
        overrides: Map<String, AppOverride>,
        mode: LimitMode,
        hasUsageAccess: Boolean,
        foregroundApp: String?,
        groups: List<AppGroup> = emptyList(),
        schedules: List<AppSchedule> = emptyList(),
    ): EnforcementPlan {
        val apps = candidates.filterNot(isAlwaysAllowed).toSet()

        // Fail safe: without usage access the app can't see use, so it can't
        // tell which apps are over their limits. Pause everything except
        // always allowed apps until access is turned back on.
        if (!hasUsageAccess) return EnforcementPlan(apps, null, MAX_WAIT)

        val decisions = apps.associateWith { pkg ->
            BlockRules.decide(
                now = now,
                zone = zone,
                usedToday = usageToday[pkg] ?: Duration.ZERO,
                limit = limits[pkg],
                downtime = downtime,
                alwaysAllowed = false,
                override = overrides[pkg] ?: AppOverride(),
                mode = mode,
                groups = GroupRules.limitedGroupsFor(pkg, groups, usageToday),
                schedules = ScheduleRules.activeFor(pkg, schedules, groups, now, zone),
            )
        }
        val blocked = decisions.filterValues { it is BlockDecision.Blocked }

        // Reminder mode never pauses apps; it only shows the reminder screen.
        val pause = if (mode == LimitMode.Reminder) emptySet() else blocked.keys
        val show = foregroundApp
            ?.let { blocked[it] as? BlockDecision.Blocked }
            ?.let { BlockRequest(foregroundApp, it.reason, it.ruleName) }

        return EnforcementPlan(pause, show, nextCheck(now, zone, apps, usageToday, limits, groups, schedules, downtime, decisions))
    }

    /** The soonest moment something could change: a limit running out, extra time ending, downtime or midnight. */
    private fun nextCheck(
        now: Instant,
        zone: ZoneId,
        apps: Set<String>,
        usageToday: Map<String, Duration>,
        limits: Map<String, AppLimit>,
        groups: List<AppGroup>,
        schedules: List<AppSchedule>,
        downtime: DowntimeSchedule,
        decisions: Map<String, BlockDecision>,
    ): Duration {
        val today = now.atZone(zone).toLocalDate()
        val waits = mutableListOf(MAX_WAIT)
        for (pkg in apps) {
            val limit = limits[pkg] ?: continue
            val left = limit.timeLeft(today, usageToday[pkg] ?: Duration.ZERO)
            if (!left.isZero) waits += left
        }
        // A group's shared total runs out at the same moment for all its apps.
        for (group in groups) {
            val limit = group.limit ?: continue
            if (group.packages.none { it in apps }) continue
            val left = limit.limitOn(today) - GroupRules.usedToday(group, usageToday)
            if (!left.isNegative && !left.isZero) waits += left
        }
        decisions.values.filterIsInstance<BlockDecision.AllowedUntil>().forEach { waits += Duration.between(now, it.until) }
        // Downtime and each schedule start or end on time.
        for (status in listOf(downtime.status(now, zone)) + schedules.map { it.status(now, zone) }) {
            when (status) {
                is DowntimeStatus.StartsAt -> waits += Duration.between(now, status.start.toInstant())
                is DowntimeStatus.ActiveUntil -> waits += Duration.between(now, status.end.toInstant())
                DowntimeStatus.Off -> Unit
            }
        }
        waits += Duration.between(now, today.plusDays(1).atStartOfDay(zone).toInstant())
        return waits.min().coerceIn(MIN_WAIT, MAX_WAIT)
    }
}
