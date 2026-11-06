package org.openscreentime.ui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.openscreentime.limits.AppLimit
import org.openscreentime.limits.LimitRepository

/** In-memory limits for tests. */
class FakeLimitRepository : LimitRepository {
    val saved = MutableStateFlow<Map<String, AppLimit>>(emptyMap())

    override val limits: Flow<List<AppLimit>> = saved.map { it.values.sortedBy(AppLimit::packageName) }
    override fun limitFor(packageName: String): Flow<AppLimit?> = saved.map { it[packageName] }
    override suspend fun save(limit: AppLimit) {
        saved.value = saved.value + (limit.packageName to limit)
    }
    override suspend fun remove(packageName: String) {
        saved.value = saved.value - packageName
    }
}
