package org.openscreentime.blocking

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/** Saved extra time per app. An interface so tests can use a fake. */
interface OverrideRepository {
    fun overrideFor(packageName: String): Flow<AppOverride>

    /** Allows [packageName] until [until], unless it's already allowed for longer. */
    suspend fun allowUntil(packageName: String, until: Instant)

    suspend fun markGraceUsed(packageName: String, day: LocalDate)
}

val Context.overrideDataStore: DataStore<Preferences> by preferencesDataStore(name = "block_overrides")

/**
 * Uses the phone's clock. Changing the date or time could stretch extra
 * time; phase 2's restriction on changing the date and time closes that gap.
 */
class DataStoreOverrideRepository(private val dataStore: DataStore<Preferences>) : OverrideRepository {

    override fun overrideFor(packageName: String): Flow<AppOverride> = dataStore.data.map { prefs ->
        AppOverride(
            allowedUntil = prefs[untilKey(packageName)]?.let(Instant::ofEpochMilli),
            graceUsedOn = prefs[graceKey(packageName)]?.let(LocalDate::ofEpochDay),
        )
    }

    override suspend fun allowUntil(packageName: String, until: Instant) {
        dataStore.edit { prefs ->
            val current = prefs[untilKey(packageName)] ?: 0L
            prefs[untilKey(packageName)] = maxOf(current, until.toEpochMilli())
        }
    }

    override suspend fun markGraceUsed(packageName: String, day: LocalDate) {
        dataStore.edit { prefs -> prefs[graceKey(packageName)] = day.toEpochDay() }
    }

    private fun untilKey(pkg: String) = longPreferencesKey("allowed_until:$pkg")
    private fun graceKey(pkg: String) = longPreferencesKey("grace_day:$pkg")
}
