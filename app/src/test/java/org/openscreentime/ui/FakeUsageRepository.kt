package org.openscreentime.ui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.openscreentime.data.HistoryLength
import org.openscreentime.usage.AppUsageTime
import org.openscreentime.usage.UsageRepository
import java.time.LocalDate

/** In-memory usage for tests. Records what the app asked it to do. */
class FakeUsageRepository : UsageRepository {
    val usageByDay = MutableStateFlow<Map<LocalDate, List<AppUsageTime>>>(emptyMap())
    var syncCount = 0
    val deletedWith = mutableListOf<HistoryLength>()

    override fun usageForDay(day: LocalDate): Flow<List<AppUsageTime>> =
        usageByDay.map { it[day].orEmpty() }

    override suspend fun sync() {
        syncCount++
    }

    override suspend fun deleteOldHistory(length: HistoryLength) {
        deletedWith += length
    }
}
