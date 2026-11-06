package org.openscreentime.blocking

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
import java.time.Instant
import java.time.LocalDate

class OverrideRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repository by lazy {
        DataStoreOverrideRepository(
            PreferenceDataStoreFactory.create(scope = scope) { tempFolder.root.resolve("overrides.preferences_pb") },
        )
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun nothingByDefault() = runBlocking {
        assertEquals(AppOverride(), repository.overrideFor("video").first())
    }

    @Test
    fun extraTimeNeverShrinks() = runBlocking {
        val later = Instant.parse("2026-11-05T16:00:00Z")
        val sooner = Instant.parse("2026-11-05T15:15:00Z")
        repository.allowUntil("video", later)
        repository.allowUntil("video", sooner)
        assertEquals(later, repository.overrideFor("video").first().allowedUntil)
    }

    @Test
    fun graceDayIsSavedPerApp() = runBlocking {
        val day = LocalDate.of(2026, 11, 5)
        repository.markGraceUsed("video", day)
        assertEquals(day, repository.overrideFor("video").first().graceUsedOn)
        assertEquals(null, repository.overrideFor("maps").first().graceUsedOn)
    }
}
