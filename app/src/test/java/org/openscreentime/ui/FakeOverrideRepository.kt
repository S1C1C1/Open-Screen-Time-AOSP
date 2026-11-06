package org.openscreentime.ui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.openscreentime.blocking.AppOverride
import org.openscreentime.blocking.OverrideRepository
import java.time.Instant
import java.time.LocalDate

/** In-memory extra time for tests. */
class FakeOverrideRepository : OverrideRepository {
    val saved = MutableStateFlow<Map<String, AppOverride>>(emptyMap())

    override fun overrideFor(packageName: String): Flow<AppOverride> = saved.map { it[packageName] ?: AppOverride() }

    override suspend fun allowUntil(packageName: String, until: Instant) {
        val current = saved.value[packageName] ?: AppOverride()
        val kept = current.allowedUntil?.takeIf { it.isAfter(until) } ?: until
        saved.value = saved.value + (packageName to current.copy(allowedUntil = kept))
    }

    override suspend fun markGraceUsed(packageName: String, day: LocalDate) {
        val current = saved.value[packageName] ?: AppOverride()
        saved.value = saved.value + (packageName to current.copy(graceUsedOn = day))
    }
}
