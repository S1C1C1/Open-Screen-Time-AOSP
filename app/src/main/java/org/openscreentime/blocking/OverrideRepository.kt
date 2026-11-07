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

    /** Lifts the block [blockKey] for [packageName] until [until], unless it's already lifted for longer. */
    suspend fun allowUntil(packageName: String, blockKey: String, until: Instant)

    suspend fun markGraceUsed(packageName: String, day: LocalDate)
}

val Context.overrideDataStore: DataStore<Preferences> by preferencesDataStore(name = "block_overrides")

/**
 * Uses the phone's clock. Changing the date or time could stretch extra
 * time; phase 2's restriction on changing the date and time closes that gap.
 */
class DataStoreOverrideRepository(private val dataStore: DataStore<Preferences>) : OverrideRepository {

    override fun overrideFor(packageName: String): Flow<AppOverride> = dataStore.data.map { prefs ->
        val prefix = untilPrefix(packageName)
        val perBlock = prefs.asMap()
            .filterKeys { it.name.startsWith(prefix) }
            .mapNotNull { (key, value) -> (value as? Long)?.let { key.name.removePrefix(prefix) to Instant.ofEpochMilli(it) } }
            .toMap()
        // Extra time saved by older versions had no block key and lifts every block.
        val legacy = prefs[legacyUntilKey(packageName)]?.let { mapOf(BlockKeys.ANY to Instant.ofEpochMilli(it)) }.orEmpty()
        AppOverride(
            allowedUntil = legacy + perBlock,
            graceUsedOn = prefs[graceKey(packageName)]?.let(LocalDate::ofEpochDay),
        )
    }

    override suspend fun allowUntil(packageName: String, blockKey: String, until: Instant) {
        dataStore.edit { prefs ->
            val key = longPreferencesKey(untilPrefix(packageName) + blockKey)
            val current = prefs[key] ?: 0L
            prefs[key] = maxOf(current, until.toEpochMilli())
            // Tidy up extra time that ran out long ago, so old entries don't pile up.
            val cutoff = until.toEpochMilli() - KEEP_EXPIRED_MILLIS
            prefs.asMap().toList().forEach { (k, v) ->
                if (k.name.startsWith(UNTIL_PREFIX) && v is Long && v < cutoff) prefs.remove(longPreferencesKey(k.name))
            }
        }
    }

    override suspend fun markGraceUsed(packageName: String, day: LocalDate) {
        dataStore.edit { prefs -> prefs[graceKey(packageName)] = day.toEpochDay() }
    }

    private fun untilPrefix(pkg: String) = "$UNTIL_PREFIX$pkg|"
    private fun legacyUntilKey(pkg: String) = longPreferencesKey("$UNTIL_PREFIX$pkg")
    private fun graceKey(pkg: String) = longPreferencesKey("grace_day:$pkg")

    private companion object {
        const val UNTIL_PREFIX = "allowed_until:"

        /** Extra time that ran out more than two days ago is no longer needed. */
        const val KEEP_EXPIRED_MILLIS = 2L * 24 * 60 * 60 * 1000
    }
}
