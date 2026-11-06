package org.openscreentime.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun newRepository(fileName: String = "settings.preferences_pb") =
        DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(scope = scope) { tempFolder.root.resolve(fileName) },
        )

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun defaultsBeforeAnythingIsSaved() = runBlocking {
        assertEquals(AppSettings(), newRepository().settings.first())
    }

    @Test
    fun completingOnboardingWithTrackingOn() = runBlocking {
        val repository = newRepository()
        repository.completeOnboarding(trackingEnabled = true)
        assertEquals(
            AppSettings(onboardingCompleted = true, trackingEnabled = true),
            repository.settings.first(),
        )
    }

    @Test
    fun completingOnboardingWithNotNow() = runBlocking {
        val repository = newRepository()
        repository.completeOnboarding(trackingEnabled = false)
        assertEquals(
            AppSettings(onboardingCompleted = true, trackingEnabled = false),
            repository.settings.first(),
        )
    }

    @Test
    fun trackingCanBeTurnedOffAndOn() = runBlocking {
        val repository = newRepository()
        repository.completeOnboarding(trackingEnabled = true)

        repository.setTrackingEnabled(false)
        assertEquals(false, repository.settings.first().trackingEnabled)

        repository.setTrackingEnabled(true)
        assertEquals(true, repository.settings.first().trackingEnabled)
        // Changing tracking never resets the welcome screen.
        assertEquals(true, repository.settings.first().onboardingCompleted)
    }

    @Test
    fun historyLengthDefaultsToFourWeeksAndCanBeChanged() = runBlocking {
        val repository = newRepository()
        assertEquals(HistoryLength.FourWeeks, repository.settings.first().historyLength)

        repository.setHistoryLength(HistoryLength.OneYear)
        assertEquals(HistoryLength.OneYear, repository.settings.first().historyLength)
    }

    @Test
    fun limitModeDefaultsToStrictAndCanBeChanged() = runBlocking {
        val repository = newRepository("mode.preferences_pb")
        assertEquals(org.openscreentime.blocking.LimitMode.Strict, repository.settings.first().limitMode)
        repository.setLimitMode(org.openscreentime.blocking.LimitMode.Reminder)
        assertEquals(org.openscreentime.blocking.LimitMode.Reminder, repository.settings.first().limitMode)
    }

    @Test
    fun unknownSavedHistoryLengthFallsBackToDefault() = runBlocking {
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            tempFolder.root.resolve("odd.preferences_pb")
        }
        dataStore.edit { it[stringPreferencesKey("history_length")] = "TenYears" }
        assertEquals(
            HistoryLength.FourWeeks,
            DataStoreSettingsRepository(dataStore).settings.first().historyLength,
        )
    }
}
