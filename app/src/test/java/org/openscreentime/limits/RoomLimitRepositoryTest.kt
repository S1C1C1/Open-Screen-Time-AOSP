package org.openscreentime.limits

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.openscreentime.data.UsageDatabase
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class RoomLimitRepositoryTest {

    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        UsageDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val repository = RoomLimitRepository(database.appLimitDao())

    @After
    fun tearDown() = database.close()

    private fun minutes(n: Long) = Duration.ofMinutes(n)

    @Test
    fun saveReadReplaceAndRemove() = runBlocking {
        assertEquals(emptyList<AppLimit>(), repository.limits.first())

        val limit = AppLimit("video", minutes(60), weekend = minutes(120))
        repository.save(limit)
        assertEquals(limit, repository.limitFor("video").first())

        val changed = AppLimit("video", minutes(45))
        repository.save(changed)
        assertEquals(listOf(changed), repository.limits.first())

        repository.remove("video")
        assertNull(repository.limitFor("video").first())
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidLimitIsRefused() = runBlocking {
        repository.save(AppLimit("video", minutes(10)))
    }
}
