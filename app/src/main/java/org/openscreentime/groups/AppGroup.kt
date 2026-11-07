package org.openscreentime.groups

import org.openscreentime.limits.LimitRules
import java.time.Duration
import java.time.LocalDate

/** A daily amount of time, optionally different on Saturday and Sunday. */
data class DailyLimit(val weekday: Duration, val weekend: Duration? = null) {
    fun limitOn(date: LocalDate): Duration =
        if (weekend != null && date.dayOfWeek in LimitRules.WEEKEND) weekend else weekday

    val isValid: Boolean get() = LimitRules.isValid(weekday) && (weekend == null || LimitRules.isValid(weekend))
}

/**
 * Apps that share the same rules. A group limit is one shared total: time
 * in all the group's apps together counts towards it. An app can be in
 * several groups. [id] is 0 for a group that hasn't been saved yet.
 */
data class AppGroup(
    val id: Long = 0,
    val name: String,
    val packages: Set<String> = emptySet(),
    val limit: DailyLimit? = null,
)

/** A group with a limit, and how much of it was used today. */
data class GroupUsage(val group: AppGroup, val limit: DailyLimit, val usedToday: Duration) {
    fun timeLeft(date: LocalDate): Duration = (limit.limitOn(date) - usedToday).coerceAtLeast(Duration.ZERO)
    fun isReached(date: LocalDate): Boolean = usedToday >= limit.limitOn(date)
}

object GroupRules {
    const val MAX_NAME_LENGTH = 40

    /** Tidies a typed name; null if nothing is left. */
    fun cleanName(name: String): String? = name.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH).ifEmpty { null }

    /** Total time today across all the group's apps. */
    fun usedToday(group: AppGroup, usageToday: Map<String, Duration>): Duration =
        group.packages.fold(Duration.ZERO) { sum, pkg -> sum + (usageToday[pkg] ?: Duration.ZERO) }

    /** For [packageName]: each of its groups that has a limit, with today's shared total. */
    fun limitedGroupsFor(packageName: String, groups: List<AppGroup>, usageToday: Map<String, Duration>): List<GroupUsage> =
        groups.mapNotNull { group ->
            val limit = group.limit ?: return@mapNotNull null
            if (packageName !in group.packages) return@mapNotNull null
            GroupUsage(group, limit, usedToday(group, usageToday))
        }
}
