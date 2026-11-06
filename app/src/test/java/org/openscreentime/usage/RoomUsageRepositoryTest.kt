package org.openscreentime.usage

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
import org.openscreentime.data.DailyUsageEntity
import org.openscreentime.data.HistoryLength
import org.openscreentime.data.UsageDatabase
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class RoomUsageRepositoryTest {

    private val zone = ZoneId.of("America/New_York")
    private val today = LocalDate.of(2026, 11, 5)
    private val now: Instant = today.atTime(18, 0).atZone(zone).toInstant()

    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        UsageDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val dao = database.dailyUsageDao()

    private var events: List<UsageEvent> = emptyList()
    private var requestedWindow: Pair<Instant, Instant>? = null

    private val repository = RoomUsageRepository(
        dao = dao,
        eventSource = { start, end ->
            requestedWindow = start to end
            events
        },
        excludedPackages = { setOf("launcher") },
        clock = Clock.fixed(now, zone),
        zone = { zone },
    )

    @After
    fun tearDown() = database.close()

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant =
        date.atTime(hour, minute).atZone(zone).toInstant()

    private fun row(date: LocalDate, pkg: String, minutes: Long) =
        DailyUsageEntity(date.toEpochDay(), pkg, Duration.ofMinutes(minutes).toMillis())

    @Test
    fun syncReadsTheLastWeekAndSavesTotals() = runBlocking {
        events = listOf(
            UsageEvent.AppResumed(at(today, 9), "mail", "Main"),
            UsageEvent.AppPaused(at(today, 9, 20), "mail", "Main"),
            UsageEvent.AppResumed(at(today, 9, 20), "launcher", "Home"),
            UsageEvent.AppPaused(at(today, 9, 25), "launcher", "Home"),
            UsageEvent.AppResumed(at(today, 10), "chat", "Main"),
            UsageEvent.AppPaused(at(today, 10, 45), "chat", "Main"),
        )
        repository.sync()

        assertEquals(today.minusDays(6).atStartOfDay(zone).toInstant() to now, requestedWindow)
        assertEquals(
            listOf(
                AppUsageTime("chat", Duration.ofMinutes(45)),
                AppUsageTime("mail", Duration.ofMinutes(20)),
            ),
            repository.usageForDay(today).first(),
        )
    }

    @Test
    fun syncNeverLowersASavedDay() = runBlocking {
        // Saved earlier, while Android still had that day's events.
        val lastWeek = today.minusDays(6)
        dao.saveKeepingLarger(listOf(row(lastWeek, "mail", 60)))

        // Android has since forgotten most of that day.
        events = listOf(
            UsageEvent.AppResumed(at(lastWeek, 22), "mail", "Main"),
            UsageEvent.AppPaused(at(lastWeek, 22, 10), "mail", "Main"),
        )
        repository.sync()

        assertEquals(
            listOf(AppUsageTime("mail", Duration.ofMinutes(60))),
            repository.usageForDay(lastWeek).first(),
        )
    }

    @Test
    fun syncRaisesTodayAsUseContinues() = runBlocking {
        dao.saveKeepingLarger(listOf(row(today, "mail", 5)))
        events = listOf(
            UsageEvent.AppResumed(at(today, 9), "mail", "Main"),
            UsageEvent.AppPaused(at(today, 9, 30), "mail", "Main"),
        )
        repository.sync()
        assertEquals(Duration.ofMinutes(30), repository.usageForDay(today).first().single().time)
    }

    @Test
    fun deleteOldHistoryKeepsExactlyTheChosenNumberOfDays() = runBlocking {
        dao.saveKeepingLarger(
            listOf(
                row(today, "a", 1),
                row(today.minusDays(6), "a", 1), // 7th day: kept with one week
                row(today.minusDays(7), "a", 1), // 8th day: deleted
                row(today.minusDays(30), "a", 1),
            ),
        )
        repository.deleteOldHistory(HistoryLength.OneWeek)

        assertEquals(1, repository.usageForDay(today).first().size)
        assertEquals(1, repository.usageForDay(today.minusDays(6)).first().size)
        assertEquals(0, repository.usageForDay(today.minusDays(7)).first().size)
        assertEquals(0, repository.usageForDay(today.minusDays(30)).first().size)
    }

    @Test
    fun oldestDayToKeepForEachSetting() {
        assertEquals(today.minusDays(6), oldestDayToKeep(today, HistoryLength.OneWeek))
        assertEquals(today.minusDays(27), oldestDayToKeep(today, HistoryLength.FourWeeks))
        assertEquals(today.minusDays(89), oldestDayToKeep(today, HistoryLength.ThreeMonths))
        assertEquals(today.minusDays(364), oldestDayToKeep(today, HistoryLength.OneYear))
    }
}
