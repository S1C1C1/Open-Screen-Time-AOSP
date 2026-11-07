package org.openscreentime.ui.schedules

import org.openscreentime.downtime.DowntimeWindow
import org.openscreentime.schedules.AppSchedule
import org.openscreentime.schedules.SchedulePreset
import org.openscreentime.schedules.ScheduleRules
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * A schedule being created or edited. Kept until Save is tapped, so the
 * app picker (a separate screen) can change the same draft.
 */
data class ScheduleDraft(
    /** 0 for a new schedule. */
    val id: Long = 0,
    val name: String = "",
    val enabled: Boolean = true,
    val days: Set<DayOfWeek> = SchedulePreset.WorkSchool.days,
    val start: LocalTime = SchedulePreset.WorkSchool.window.start,
    val end: LocalTime = SchedulePreset.WorkSchool.window.end,
    val packages: Set<String> = emptySet(),
    val groupIds: Set<Long> = emptySet(),
) {
    val isNew: Boolean get() = id == 0L

    val crossesMidnight: Boolean get() = end < start

    /** Save needs a name, at least one day, and different start and end times. */
    val canSave: Boolean get() = ScheduleRules.cleanName(name) != null && days.isNotEmpty() && start != end

    fun toggleDay(day: DayOfWeek): ScheduleDraft = copy(days = if (day in days) days - day else days + day)

    fun toggleApp(packageName: String): ScheduleDraft =
        copy(packages = if (packageName in packages) packages - packageName else packages + packageName)

    fun toggleGroup(groupId: Long): ScheduleDraft =
        copy(groupIds = if (groupId in groupIds) groupIds - groupId else groupIds + groupId)

    fun toSchedule(): AppSchedule = AppSchedule(
        id = id,
        name = ScheduleRules.cleanName(name).orEmpty(),
        enabled = enabled,
        days = days,
        window = DowntimeWindow(start, end),
        packages = packages,
        groupIds = groupIds,
    )

    companion object {
        fun from(schedule: AppSchedule) = ScheduleDraft(
            id = schedule.id,
            name = schedule.name,
            enabled = schedule.enabled,
            days = schedule.days,
            start = schedule.window.start,
            end = schedule.window.end,
            packages = schedule.packages,
            groupIds = schedule.groupIds,
        )

        fun from(preset: SchedulePreset, name: String) = ScheduleDraft(
            name = name,
            days = preset.days,
            start = preset.window.start,
            end = preset.window.end,
        )
    }
}
