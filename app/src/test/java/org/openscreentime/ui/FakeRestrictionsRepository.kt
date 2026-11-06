package org.openscreentime.ui

import kotlinx.coroutines.flow.MutableStateFlow
import org.openscreentime.restrictions.Lift
import org.openscreentime.restrictions.Restriction
import org.openscreentime.restrictions.RestrictionsRepository
import org.openscreentime.restrictions.RestrictionsState

/** In-memory restrictions for tests. */
class FakeRestrictionsRepository : RestrictionsRepository {
    override val state = MutableStateFlow(RestrictionsState())

    override suspend fun setEnabled(restriction: Restriction, enabled: Boolean) {
        val s = state.value
        state.value = RestrictionsState(
            enabled = if (enabled) s.enabled + restriction else s.enabled - restriction,
            lifts = s.lifts - restriction,
        )
    }

    override suspend fun setLift(restriction: Restriction, lift: Lift?) {
        val s = state.value
        state.value = s.copy(lifts = if (lift == null) s.lifts - restriction else s.lifts + (restriction to lift))
    }
}
