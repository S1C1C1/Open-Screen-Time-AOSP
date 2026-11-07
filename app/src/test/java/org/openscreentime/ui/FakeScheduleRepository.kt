package org.openscreentime.ui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.openscreentime.schedules.AppSchedule
import org.openscreentime.schedules.ScheduleRepository

/** In-memory schedules for tests. */
class FakeScheduleRepository(initial: List<AppSchedule> = emptyList()) : ScheduleRepository {
    val saved = MutableStateFlow(initial.associateBy { it.id })
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    override val schedules: Flow<List<AppSchedule>> = saved.map { it.values.sortedBy { schedule -> schedule.name.lowercase() } }

    override suspend fun save(schedule: AppSchedule): Long {
        val id = if (schedule.id == 0L) nextId++ else schedule.id
        saved.value = saved.value + (id to schedule.copy(id = id))
        return id
    }

    override suspend fun delete(scheduleId: Long) {
        saved.value = saved.value - scheduleId
    }
}
