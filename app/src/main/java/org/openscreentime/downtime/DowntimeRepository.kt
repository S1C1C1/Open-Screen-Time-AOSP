package org.openscreentime.downtime

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalTime

/** The saved downtime schedule. An interface so tests can use a fake. */
interface DowntimeRepository {
    val schedule: Flow<DowntimeSchedule>
    suspend fun save(schedule: DowntimeSchedule)
}

val Context.downtimeDataStore: DataStore<Preferences> by preferencesDataStore(name = "downtime")

/**
 * Saves the schedule in its own small settings file: a few switches and,
 * for each day, whether it has downtime and its start and end as minutes
 * after midnight.
 */
class DataStoreDowntimeRepository(private val dataStore: DataStore<Preferences>) : DowntimeRepository {

    override val schedule: Flow<DowntimeSchedule> = dataStore.data.map { prefs ->
        val defaults = DowntimeSchedule()
        DowntimeSchedule(
            enabled = prefs[ENABLED] ?: defaults.enabled,
            sameEveryDay = prefs[SAME_EVERY_DAY] ?: defaults.sameEveryDay,
            // readDay already gives the default for days never saved; null means "no downtime".
            days = DayOfWeek.entries.associateWith { day -> readDay(prefs, day) },
        )
    }

    override suspend fun save(schedule: DowntimeSchedule) {
        dataStore.edit { prefs ->
            prefs[ENABLED] = schedule.enabled
            prefs[SAME_EVERY_DAY] = schedule.sameEveryDay
            DayOfWeek.entries.forEach { day ->
                val window = schedule.days[day]
                prefs[onKey(day)] = window != null
                if (window != null) {
                    prefs[startKey(day)] = window.start.toSecondOfDay() / 60
                    prefs[endKey(day)] = window.end.toSecondOfDay() / 60
                }
            }
        }
    }

    /** A day's window; null when the day has no downtime. Unsaved days use the default. */
    private fun readDay(prefs: Preferences, day: DayOfWeek): DowntimeWindow? {
        val on = prefs[onKey(day)] ?: return DowntimeWindow.DEFAULT
        if (!on) return null
        val start = prefs[startKey(day)] ?: return DowntimeWindow.DEFAULT
        val end = prefs[endKey(day)] ?: return DowntimeWindow.DEFAULT
        return runCatching { DowntimeWindow(minutesToTime(start), minutesToTime(end)) }
            .getOrDefault(DowntimeWindow.DEFAULT)
    }

    private fun minutesToTime(minutes: Int) = LocalTime.of(minutes / 60 % 24, minutes % 60)

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val SAME_EVERY_DAY = booleanPreferencesKey("same_every_day")
        fun onKey(day: DayOfWeek) = booleanPreferencesKey("${day.name.lowercase()}_on")
        fun startKey(day: DayOfWeek) = intPreferencesKey("${day.name.lowercase()}_start_minute")
        fun endKey(day: DayOfWeek) = intPreferencesKey("${day.name.lowercase()}_end_minute")
    }
}
