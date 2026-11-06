package org.openscreentime.limits

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.openscreentime.data.AppLimitDao
import org.openscreentime.data.AppLimitEntity
import java.time.Duration

/** Saved app limits. An interface so tests can use a fake. */
interface LimitRepository {
    val limits: Flow<List<AppLimit>>

    fun limitFor(packageName: String): Flow<AppLimit?>

    /** Adds or replaces the limit for its app. Invalid limits are refused. */
    suspend fun save(limit: AppLimit)

    suspend fun remove(packageName: String)
}

class RoomLimitRepository(private val dao: AppLimitDao) : LimitRepository {

    override val limits: Flow<List<AppLimit>> = dao.all().map { rows -> rows.map { it.toLimit() } }

    override fun limitFor(packageName: String): Flow<AppLimit?> =
        dao.forApp(packageName).map { it?.toLimit() }

    override suspend fun save(limit: AppLimit) {
        require(LimitRules.isValid(limit)) { "Limit must be 15 min to 23 hr 45 min, in 15-minute steps" }
        dao.upsert(
            AppLimitEntity(
                packageName = limit.packageName,
                weekdayMinutes = limit.weekday.toMinutes().toInt(),
                weekendMinutes = limit.weekend?.toMinutes()?.toInt(),
            ),
        )
    }

    override suspend fun remove(packageName: String) = dao.delete(packageName)

    private fun AppLimitEntity.toLimit() = AppLimit(
        packageName = packageName,
        weekday = Duration.ofMinutes(weekdayMinutes.toLong()),
        weekend = weekendMinutes?.let { Duration.ofMinutes(it.toLong()) },
    )
}
