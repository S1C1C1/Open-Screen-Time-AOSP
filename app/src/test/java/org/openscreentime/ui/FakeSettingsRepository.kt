package org.openscreentime.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.openscreentime.data.AppSettings
import org.openscreentime.data.HistoryLength
import org.openscreentime.data.SettingsRepository

/** In-memory settings for tests. */
class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<AppSettings> = state

    override suspend fun completeOnboarding(trackingEnabled: Boolean) {
        state.value = state.value.copy(onboardingCompleted = true, trackingEnabled = trackingEnabled)
    }

    override suspend fun setTrackingEnabled(enabled: Boolean) {
        state.value = state.value.copy(trackingEnabled = enabled)
    }

    override suspend fun setHistoryLength(length: HistoryLength) {
        state.value = state.value.copy(historyLength = length)
    }
}
