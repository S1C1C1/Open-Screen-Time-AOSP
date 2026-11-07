package org.openscreentime.groups

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.openscreentime.data.UsageDatabase
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class RoomGroupRepositoryTest {

    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        UsageDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val repository = RoomGroupRepository(database.appGroupDao())

    @After
    fun tearDown() = database.close()

    private fun minutes(n: Long) = Duration.ofMinutes(n)

    @Test
    fun saveReadEditAndDelete() = runBlocking {
        val id = repository.save(AppGroup(name = " Social ", packages = setOf("chat", "photos"), limit = DailyLimit(minutes(60), minutes(90))))
        assertEquals(
            listOf(AppGroup(id, "Social", setOf("chat", "photos"), DailyLimit(minutes(60), minutes(90)))),
            repository.groups.first(),
        )

        // Editing replaces the apps and the limit.
        repository.save(AppGroup(id, "Social", setOf("chat"), limit = null))
        assertEquals(listOf(AppGroup(id, "Social", setOf("chat"))), repository.groups.first())

        repository.delete(id)
        assertEquals(emptyList<AppGroup>(), repository.groups.first())
    }

    @Test
    fun groupsAreSortedByNameAndAnAppCanBeInSeveral() = runBlocking {
        repository.save(AppGroup(name = "games", packages = setOf("chat")))
        repository.save(AppGroup(name = "Apps", packages = setOf("chat")))
        assertEquals(listOf("Apps", "games"), repository.groups.first().map { it.name })
    }

    @Test(expected = IllegalArgumentException::class)
    fun aGroupNeedsAName() = runBlocking {
        repository.save(AppGroup(name = "  "))
        Unit
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidLimitIsRefused() = runBlocking {
        repository.save(AppGroup(name = "Social", limit = DailyLimit(minutes(10))))
        Unit
    }
}
