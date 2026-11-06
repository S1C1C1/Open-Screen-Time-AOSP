package org.openscreentime.restrictions

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Which restrictions are on, and any lifts. An interface so tests can use a fake. */
interface RestrictionsRepository {
    val state: Flow<RestrictionsState>
    suspend fun setEnabled(restriction: Restriction, enabled: Boolean)
    suspend fun setLift(restriction: Restriction, lift: Lift?)
}

val Context.restrictionsDataStore: DataStore<Preferences> by preferencesDataStore(name = "restrictions")

class DataStoreRestrictionsRepository(private val dataStore: DataStore<Preferences>) : RestrictionsRepository {

    override val state: Flow<RestrictionsState> = dataStore.data.map { prefs ->
        RestrictionsState(
            enabled = prefs[ENABLED].orEmpty().mapNotNull { name -> Restriction.entries.find { it.name == name } }.toSet(),
            lifts = Restriction.entries.mapNotNull { restriction ->
                val boot = prefs[bootKey(restriction)] ?: return@mapNotNull null
                val ends = prefs[endsKey(restriction)] ?: return@mapNotNull null
                restriction to Lift(boot, ends)
            }.toMap(),
        )
    }

    override suspend fun setEnabled(restriction: Restriction, enabled: Boolean) {
        dataStore.edit { prefs ->
            val names = prefs[ENABLED].orEmpty()
            prefs[ENABLED] = if (enabled) names + restriction.name else names - restriction.name
            // Switching a restriction off or on again starts without a lift.
            prefs.remove(bootKey(restriction))
            prefs.remove(endsKey(restriction))
        }
    }

    override suspend fun setLift(restriction: Restriction, lift: Lift?) {
        dataStore.edit { prefs ->
            if (lift == null) {
                prefs.remove(bootKey(restriction))
                prefs.remove(endsKey(restriction))
            } else {
                prefs[bootKey(restriction)] = lift.bootCount
                prefs[endsKey(restriction)] = lift.endsAtSinceBootMillis
            }
        }
    }

    private companion object {
        // Saved by name. Don't rename Restriction values without migrating.
        val ENABLED = stringSetPreferencesKey("enabled")
        fun bootKey(r: Restriction) = intPreferencesKey("lift_boot_${r.name}")
        fun endsKey(r: Restriction) = longPreferencesKey("lift_ends_${r.name}")
    }
}
