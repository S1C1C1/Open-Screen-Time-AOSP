package org.openscreentime.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.openscreentime.blocking.LimitMode

/** One settings file in the app's private storage. */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Saves [AppSettings] on the phone with AndroidX DataStore. */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            onboardingCompleted = prefs[ONBOARDING_COMPLETED] ?: false,
            trackingEnabled = prefs[TRACKING_ENABLED] ?: false,
            historyLength = prefs[HISTORY_LENGTH]
                ?.let { saved -> HistoryLength.entries.find { it.name == saved } }
                ?: HistoryLength.Default,
            limitMode = prefs[LIMIT_MODE]
                ?.let { saved -> LimitMode.entries.find { it.name == saved } }
                ?: LimitMode.Default,
        )
    }

    override suspend fun completeOnboarding(trackingEnabled: Boolean) {
        // One edit, so both values are saved together or not at all.
        dataStore.edit { prefs ->
            prefs[ONBOARDING_COMPLETED] = true
            prefs[TRACKING_ENABLED] = trackingEnabled
        }
    }

    override suspend fun setTrackingEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[TRACKING_ENABLED] = enabled }
    }

    override suspend fun setHistoryLength(length: HistoryLength) {
        dataStore.edit { prefs -> prefs[HISTORY_LENGTH] = length.name }
    }

    override suspend fun setLimitMode(mode: LimitMode) {
        dataStore.edit { prefs -> prefs[LIMIT_MODE] = mode.name }
    }

    private companion object {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val TRACKING_ENABLED = booleanPreferencesKey("tracking_enabled")

        // Saved by name. Don't rename HistoryLength values without migrating.
        val HISTORY_LENGTH = stringPreferencesKey("history_length")

        // Saved by name. Don't rename LimitMode values without migrating.
        val LIMIT_MODE = stringPreferencesKey("limit_mode")
    }
}
