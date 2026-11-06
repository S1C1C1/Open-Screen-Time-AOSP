package org.openscreentime.downtime

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
import java.time.DayOfWeek
import java.time.LocalTime

class DataStoreDowntimeRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repository by lazy {
        DataStoreDowntimeRepository(
            PreferenceDataStoreFactory.create(scope = scope) { tempFolder.root.resolve("downtime.preferences_pb") },
        )
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun defaultsBeforeAnythingIsSaved() = runBlocking {
        assertEquals(DowntimeSchedule(), repository.schedule.first())
    }

    @Test
    fun savesEverything() = runBlocking {
        val schedule = DowntimeSchedule(enabled = true)
            .withEveryDay(DowntimeWindow(LocalTime.of(22, 15), LocalTime.of(6, 45)))
            .copy(sameEveryDay = false)
            .withDay(DayOfWeek.SATURDAY, DowntimeWindow(LocalTime.of(23, 0), LocalTime.of(9, 20)))
            .withDay(DayOfWeek.SUNDAY, null)
        repository.save(schedule)
        assertEquals(schedule, repository.schedule.first())
    }
}
