package org.openscreentime.usage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.openscreentime.data.DailyUsageDao
import org.openscreentime.data.DailyUsageEntity
import org.openscreentime.data.HistoryLength
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/** Time one app was used. */
data class AppUsageTime(val packageName: String, val time: Duration)

/** Saved app usage. An interface so tests can use a fake. */
interface UsageRepository {
    /** Apps used on [day], most used first. */
    fun usageForDay(day: LocalDate): Flow<List<AppUsageTime>>

    /** Apps used on each day from [from] to [to], inclusive, most used first. Days without use are left out. */
    fun usageForDays(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, List<AppUsageTime>>>

    /** Reads recent events from Android and saves the daily totals. */
    suspend fun sync()

    /** Like [sync], but only today and yesterday: quick enough to run every few seconds while the phone is in use. */
    suspend fun syncToday()

    /** Deletes days older than [length] allows. */
    suspend fun deleteOldHistory(length: HistoryLength)
}

/**
 * Oldest day to keep. With 7 days of history, today and the 6 days before
 * it are kept.
 */
fun oldestDayToKeep(today: LocalDate, length: HistoryLength): LocalDate =
    today.minusDays(length.days - 1L)

class RoomUsageRepository(
    private val dao: DailyUsageDao,
    private val eventSource: UsageEventSource,
    private val excludedPackages: () -> Set<String>,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : UsageRepository {

    override fun usageForDay(day: LocalDate): Flow<List<AppUsageTime>> =
        dao.forDay(day.toEpochDay()).map { rows ->
            rows.map { AppUsageTime(it.packageName, Duration.ofMillis(it.foregroundMillis)) }
        }

    override fun usageForDays(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, List<AppUsageTime>>> =
        dao.forDays(from.toEpochDay(), to.toEpochDay()).map { rows ->
            rows.groupBy { LocalDate.ofEpochDay(it.epochDay) }
                .mapValues { (_, dayRows) ->
                    dayRows
                        .map { AppUsageTime(it.packageName, Duration.ofMillis(it.foregroundMillis)) }
                        .sortedWith(compareByDescending<AppUsageTime> { it.time }.thenBy { it.packageName })
                }
        }

    // Recalculate the last week every time. Days Android has already
    // forgotten can't lose time, because saving keeps the larger value.
    override suspend fun sync() = syncDays(SYNC_DAYS)

    // Starts from yesterday, so an app opened before midnight and still open
    // counts towards today from midnight on. Starting at midnight would miss
    // it until it was closed, letting someone stay in an app past their limit.
    override suspend fun syncToday() = syncDays(2)

    private suspend fun syncDays(days: Int) {
        val zone = zone()
        val now = clock.instant()
        val today = now.atZone(zone).toLocalDate()
        val windowStart = today.minusDays(days - 1L).atStartOfDay(zone).toInstant()

        val totals = withContext(Dispatchers.IO) {
            UsageCalculator.dailyTotals(
                events = eventSource.events(windowStart, now),
                windowStart = windowStart,
                windowEnd = now,
                zone = zone,
                excludedPackages = excludedPackages(),
            )
        }
        val rows = totals.flatMap { (day, apps) ->
            apps.map { (pkg, time) -> DailyUsageEntity(day.toEpochDay(), pkg, time.toMillis()) }
        }
        dao.saveKeepingLarger(rows)
    }

    override suspend fun deleteOldHistory(length: HistoryLength) {
        val today = clock.instant().atZone(zone()).toLocalDate()
        dao.deleteBefore(oldestDayToKeep(today, length).toEpochDay())
    }

    private companion object {
        const val SYNC_DAYS = 7
    }
}
