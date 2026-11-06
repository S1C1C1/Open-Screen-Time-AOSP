package org.openscreentime.data

import kotlinx.coroutines.flow.Flow
import org.openscreentime.blocking.LimitMode

/** How long usage history is kept. Counts today as one day. */
enum class HistoryLength(val days: Int) {
    OneWeek(7),
    FourWeeks(28),
    ThreeMonths(90),
    OneYear(365),
    ;

    companion object {
        val Default = FourWeeks
    }
}

/** App-wide choices the user has made. */
data class AppSettings(
    /** True once the user has finished (or skipped) the welcome screen. */
    val onboardingCompleted: Boolean = false,
    /** True when the user has chosen to turn on screen time tracking. */
    val trackingEnabled: Boolean = false,
    val historyLength: HistoryLength = HistoryLength.Default,
    val limitMode: LimitMode = LimitMode.Default,
)

/** Reads and saves [AppSettings]. An interface so tests can use a fake. */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    /** Marks the welcome screen as done and saves the user's tracking choice. */
    suspend fun completeOnboarding(trackingEnabled: Boolean)

    suspend fun setTrackingEnabled(enabled: Boolean)

    suspend fun setHistoryLength(length: HistoryLength)

    suspend fun setLimitMode(mode: LimitMode)
}
