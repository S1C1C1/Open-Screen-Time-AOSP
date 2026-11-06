package org.openscreentime.data

import kotlinx.coroutines.flow.Flow

/** App-wide choices the user has made. */
data class AppSettings(
    /** True once the user has finished (or skipped) the welcome screen. */
    val onboardingCompleted: Boolean = false,
    /** True when the user has chosen to turn on screen time tracking. */
    val trackingEnabled: Boolean = false,
)

/** Reads and saves [AppSettings]. An interface so tests can use a fake. */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    /** Marks the welcome screen as done and saves the user's tracking choice. */
    suspend fun completeOnboarding(trackingEnabled: Boolean)

    suspend fun setTrackingEnabled(enabled: Boolean)
}
