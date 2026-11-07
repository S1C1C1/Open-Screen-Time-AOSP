package org.openscreentime.schedules

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
import org.openscreentime.downtime.DowntimeWindow
import org.openscreentime.groups.AppGroup
import org.openscreentime.groups.RoomGroupRepository
import java.time.DayOfWeek
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class RoomScheduleRepositoryTest {

    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        UsageDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val repository = RoomScheduleRepository(database.appScheduleDao())
    private val groups = RoomGroupRepository(database.appGroupDao())

    @After
    fun tearDown() = database.close()

    private val bedtime = AppSchedule(
        name = " Bedtime ",
        days = SchedulePreset.Bedtime.days,
        window = SchedulePreset.Bedtime.window,
        packages = setOf("chat", "video"),
    )

    @Test
    fun saveReadEditAndDelete() = runBlocking {
        val groupId = groups.save(AppGroup(name = "Games", packages = setOf("puzzle")))
        val id = repository.save(bedtime.copy(groupIds = setOf(groupId)))
        assertEquals(listOf(bedtime.copy(id = id, name = "Bedtime", groupIds = setOf(groupId))), repository.schedules.first())

        val edited = AppSchedule(
            id = id,
            name = "Bedtime",
            enabled = false,
            days = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY),
            window = DowntimeWindow(LocalTime.of(23, 30), LocalTime.of(7, 15)),
            packages = setOf("video"),
        )
        repository.save(edited)
        assertEquals(listOf(edited), repository.schedules.first())

        repository.delete(id)
        assertEquals(emptyList<AppSchedule>(), repository.schedules.first())
    }

    @Test
    fun deletingAGroupRemovesItFromSchedules() = runBlocking {
        val groupId = groups.save(AppGroup(name = "Games", packages = setOf("puzzle")))
        repository.save(bedtime.copy(groupIds = setOf(groupId)))
        groups.delete(groupId)
        assertEquals(emptySet<Long>(), repository.schedules.first().single().groupIds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aScheduleNeedsAName() = runBlocking {
        repository.save(bedtime.copy(name = " "))
        Unit
    }

    @Test(expected = IllegalArgumentException::class)
    fun aScheduleNeedsADay() = runBlocking {
        repository.save(bedtime.copy(days = emptySet()))
        Unit
    }
}
