package org.openscreentime.ui

import kotlinx.coroutines.flow.MutableStateFlow
import org.openscreentime.downtime.DowntimeRepository
import org.openscreentime.downtime.DowntimeSchedule

/** In-memory downtime schedule for tests. */
class FakeDowntimeRepository : DowntimeRepository {
    override val schedule = MutableStateFlow(DowntimeSchedule())
    override suspend fun save(schedule: DowntimeSchedule) {
        this.schedule.value = schedule
    }
}
