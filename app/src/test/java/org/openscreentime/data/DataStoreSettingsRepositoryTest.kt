package org.openscreentime.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
}
