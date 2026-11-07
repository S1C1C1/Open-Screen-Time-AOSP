package org.openscreentime.groups

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.openscreentime.data.AppGroupDao
import org.openscreentime.data.AppGroupEntity
import java.time.Duration

/** Saved app groups. An interface so tests can use a fake. */
interface GroupRepository {
    val groups: Flow<List<AppGroup>>

    /** Adds a new group (id 0) or replaces an existing one. Returns its id. */
    suspend fun save(group: AppGroup): Long

    suspend fun delete(groupId: Long)
}

class RoomGroupRepository(private val dao: AppGroupDao) : GroupRepository {

    override val groups: Flow<List<AppGroup>> = dao.all().map { rows ->
        rows.map { row ->
            AppGroup(
                id = row.group.id,
                name = row.group.name,
                packages = row.members.map { it.packageName }.toSet(),
                limit = row.group.limitWeekdayMinutes?.let { weekday ->
                    DailyLimit(
                        weekday = Duration.ofMinutes(weekday.toLong()),
                        weekend = row.group.limitWeekendMinutes?.let { Duration.ofMinutes(it.toLong()) },
                    )
                },
            )
        }
    }

    override suspend fun save(group: AppGroup): Long {
        val name = requireNotNull(GroupRules.cleanName(group.name)) { "A group needs a name" }
        require(group.limit?.isValid != false) { "Group limit must be 15 min to 23 hr 45 min, in 15-minute steps" }
        return dao.save(
            AppGroupEntity(
                id = group.id,
                name = name,
                limitWeekdayMinutes = group.limit?.weekday?.toMinutes()?.toInt(),
                limitWeekendMinutes = group.limit?.weekend?.toMinutes()?.toInt(),
            ),
            group.packages,
        )
    }

    override suspend fun delete(groupId: Long) = dao.delete(groupId)
}
