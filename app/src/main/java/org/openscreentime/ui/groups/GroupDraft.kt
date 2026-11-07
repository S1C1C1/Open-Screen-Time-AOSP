package org.openscreentime.ui.groups

import org.openscreentime.groups.AppGroup
import org.openscreentime.groups.DailyLimit
import org.openscreentime.groups.GroupRules
import org.openscreentime.limits.LimitRules
import java.time.Duration

/**
 * A group being created or edited. Kept until Save is tapped, so the app
 * picker (a separate screen) can change the same draft.
 */
data class GroupDraft(
    /** 0 for a new group. */
    val id: Long = 0,
    val name: String = "",
    val packages: Set<String> = emptySet(),
    val limitOn: Boolean = false,
    val weekdayMinutes: Long = LimitRules.DEFAULT.toMinutes(),
    val separateWeekend: Boolean = false,
    val weekendMinutes: Long = LimitRules.DEFAULT.toMinutes(),
) {
    val isNew: Boolean get() = id == 0L

    /** Save is only possible once the group has a name. */
    val canSave: Boolean get() = GroupRules.cleanName(name) != null

    fun toggle(packageName: String): GroupDraft =
        copy(packages = if (packageName in packages) packages - packageName else packages + packageName)

    fun toGroup(): AppGroup = AppGroup(
        id = id,
        name = GroupRules.cleanName(name).orEmpty(),
        packages = packages,
        limit = if (limitOn) {
            DailyLimit(
                weekday = Duration.ofMinutes(weekdayMinutes),
                weekend = if (separateWeekend) Duration.ofMinutes(weekendMinutes) else null,
            )
        } else {
            null
        },
    )

    companion object {
        fun from(group: AppGroup): GroupDraft {
            val limit = group.limit
            return GroupDraft(
                id = group.id,
                name = group.name,
                packages = group.packages,
                limitOn = limit != null,
                weekdayMinutes = (limit?.weekday ?: LimitRules.DEFAULT).toMinutes(),
                separateWeekend = limit?.weekend != null,
                weekendMinutes = (limit?.weekend ?: limit?.weekday ?: LimitRules.DEFAULT).toMinutes(),
            )
        }
    }
}
