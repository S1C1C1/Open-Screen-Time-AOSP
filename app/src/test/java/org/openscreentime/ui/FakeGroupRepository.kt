package org.openscreentime.ui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.openscreentime.groups.AppGroup
import org.openscreentime.groups.GroupRepository

/** In-memory app groups for tests. */
class FakeGroupRepository(initial: List<AppGroup> = emptyList()) : GroupRepository {
    val saved = MutableStateFlow(initial.associateBy { it.id })
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    override val groups: Flow<List<AppGroup>> = saved.map { it.values.sortedBy { group -> group.name.lowercase() } }

    override suspend fun save(group: AppGroup): Long {
        val id = if (group.id == 0L) nextId++ else group.id
        saved.value = saved.value + (id to group.copy(id = id))
        return id
    }

    override suspend fun delete(groupId: Long) {
        saved.value = saved.value - groupId
    }
}
