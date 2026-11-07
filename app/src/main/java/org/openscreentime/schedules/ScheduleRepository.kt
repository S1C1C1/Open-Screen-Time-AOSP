package org.openscreentime.schedules

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.openscreentime.data.AppScheduleDao
import org.openscreentime.data.AppScheduleEntity
import org.openscreentime.downtime.DowntimeWindow
import java.time.DayOfWeek
import java.time.LocalTime

/** Saved schedules. An interface so tests can use a fake. */
interface ScheduleRepository {
    val schedules: Flow<List<AppSchedule>>

    /** Adds a new schedule (id 0) or replaces an existing one. Returns its id. */
    suspend fun save(schedule: AppSchedule): Long

    suspend fun delete(scheduleId: Long)
}

class RoomScheduleRepository(private val dao: AppScheduleDao) : ScheduleRepository {

    override val schedules: Flow<List<AppSchedule>> = dao.all().map { rows ->
        rows.map { row ->
            AppSchedule(
                id = row.schedule.id,
                name = row.schedule.name,
                enabled = row.schedule.enabled,
                days = DaysMask.toDays(row.schedule.days),
                window = DowntimeWindow(minuteToTime(row.schedule.startMinute), minuteToTime(row.schedule.endMinute)),
                packages = row.apps.map { it.packageName }.toSet(),
                groupIds = row.groups.map { it.groupId }.toSet(),
            )
        }
    }

    override suspend fun save(schedule: AppSchedule): Long {
        val name = requireNotNull(ScheduleRules.cleanName(schedule.name)) { "A schedule needs a name" }
        require(schedule.days.isNotEmpty()) { "A schedule needs at least one day" }
        return dao.save(
            AppScheduleEntity(
                id = schedule.id,
                name = name,
                enabled = schedule.enabled,
                days = DaysMask.fromDays(schedule.days),
                startMinute = schedule.window.start.toSecondOfDay() / 60,
                endMinute = schedule.window.end.toSecondOfDay() / 60,
            ),
            schedule.packages,
            schedule.groupIds,
        )
    }

    override suspend fun delete(scheduleId: Long) = dao.delete(scheduleId)

    private fun minuteToTime(minute: Int): LocalTime = LocalTime.of(minute / 60, minute % 60)
}

/** Stores a set of days as one number: Monday = 1, Tuesday = 2, … Sunday = 64. */
object DaysMask {
    fun fromDays(days: Set<DayOfWeek>): Int = days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

    fun toDays(mask: Int): Set<DayOfWeek> = DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()
}
