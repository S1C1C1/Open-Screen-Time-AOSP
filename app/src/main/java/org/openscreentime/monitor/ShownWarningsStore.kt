package org.openscreentime.monitor

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Remembers which warnings were shown, so a restart doesn't repeat them. */
interface ShownWarningsStore {
    suspend fun shown(): Set<String>

    /** Adds [keys] and forgets warnings from before [today] minus a day (days since 1970). */
    suspend fun add(keys: Collection<String>, today: Long)
}

val Context.shownWarningsDataStore: DataStore<Preferences> by preferencesDataStore(name = "shown_warnings")

class DataStoreShownWarningsStore(private val dataStore: DataStore<Preferences>) : ShownWarningsStore {
    override suspend fun shown(): Set<String> = dataStore.data.map { it[KEY] ?: emptySet() }.first()

    override suspend fun add(keys: Collection<String>, today: Long) {
        if (keys.isEmpty()) return
        dataStore.edit { prefs ->
            prefs[KEY] = ((prefs[KEY] ?: emptySet()) + keys)
                .filter { key -> (dayOf(key) ?: today) >= today - 1 }
                .toSet()
        }
    }

    private companion object {
        val KEY = stringSetPreferencesKey("shown")
    }
}

/** The day (since 1970) a warning key belongs to, from the number at its end. */
internal fun dayOf(key: String): Long? {
    val number = key.substringAfterLast(':').toLongOrNull() ?: return null
    // Limit and group keys end in a day; downtime and schedule keys end in seconds since 1970.
    val inSeconds = key.startsWith("downtime:") || key.startsWith("schedule:")
    return if (inSeconds) Math.floorDiv(number, 86_400L) else number
}
