package org.openscreentime.blocking

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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
        repository.allowUntil("video", BlockKeys.LIMIT, later)
        repository.allowUntil("video", BlockKeys.LIMIT, sooner)
        assertEquals(mapOf(BlockKeys.LIMIT to later), repository.overrideFor("video").first().allowedUntil)
    }

    @Test
    fun extraTimeIsKeptPerBlock() = runBlocking {
        val limitUntil = Instant.parse("2026-11-05T23:00:00Z")
        val scheduleUntil = Instant.parse("2026-11-05T16:00:00Z")
        repository.allowUntil("video", BlockKeys.LIMIT, limitUntil)
        repository.allowUntil("video", BlockKeys.schedule(3), scheduleUntil)
        repository.allowUntil("maps", BlockKeys.DOWNTIME, scheduleUntil)
        assertEquals(
            mapOf(BlockKeys.LIMIT to limitUntil, BlockKeys.schedule(3) to scheduleUntil),
            repository.overrideFor("video").first().allowedUntil,
        )
    }

    @Test
    fun extraTimeThatRanOutLongAgoIsTidiedAway() = runBlocking {
        val longAgo = Instant.parse("2026-11-01T10:00:00Z")
        val now = Instant.parse("2026-11-05T15:00:00Z")
        repository.allowUntil("maps", BlockKeys.LIMIT, longAgo)
        repository.allowUntil("video", BlockKeys.LIMIT, now)
        assertEquals(emptyMap<String, Instant>(), repository.overrideFor("maps").first().allowedUntil)
    }

    @Test
    fun extraTimeFromOlderVersionsStillLiftsEveryBlock() = runBlocking {
        val until = Instant.parse("2026-11-05T16:00:00Z")
        val store = PreferenceDataStoreFactory.create(scope = scope) { tempFolder.root.resolve("legacy.preferences_pb") }
        store.edit { it[longPreferencesKey("allowed_until:video")] = until.toEpochMilli() }
        val override = DataStoreOverrideRepository(store).overrideFor("video").first()
        assertEquals(until, override.untilFor(BlockKeys.DOWNTIME))
        assertEquals(until, override.untilFor(BlockKeys.schedule(1)))
    }

    @Test
    fun graceDayIsSavedPerApp() = runBlocking {
        val day = LocalDate.of(2026, 11, 5)
        repository.markGraceUsed("video", day)
        assertEquals(day, repository.overrideFor("video").first().graceUsedOn)
        assertEquals(null, repository.overrideFor("maps").first().graceUsedOn)
    }
}
