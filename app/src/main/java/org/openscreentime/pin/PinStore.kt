package org.openscreentime.pin

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** A wait in progress, timed on the clock that counts from phone start. */
data class Lockout(
    val bootCount: Int,
    val endsAtSinceBootMillis: Long,
    val durationMillis: Long,
)

/** Everything saved about the PIN. */
class PinState(
    val credential: PinCredential? = null,
    val failedAttempts: Int = 0,
    val lockout: Lockout? = null,
)

/** Saves [PinState]. An interface so tests can keep it in memory. */
interface PinStore {
    val isPinSet: Flow<Boolean>
    suspend fun read(): PinState
    suspend fun write(state: PinState)
}

/** Separate from other settings, so PIN data is kept apart and easy to reason about. */
val Context.pinDataStore: DataStore<Preferences> by preferencesDataStore(name = "pin")

class DataStorePinStore(private val dataStore: DataStore<Preferences>) : PinStore {

    override val isPinSet: Flow<Boolean> = dataStore.data.map { it[HASH] != null }

    override suspend fun read(): PinState {
        val prefs = dataStore.data.first()
        val credential = run {
            val salt = prefs[SALT] ?: return@run null
            val hash = prefs[HASH] ?: return@run null
            val iterations = prefs[ITERATIONS] ?: return@run null
            PinCredential(decode(salt), decode(hash), iterations)
        }
        val lockout = run {
            val boot = prefs[LOCKOUT_BOOT] ?: return@run null
            val endsAt = prefs[LOCKOUT_ENDS_AT] ?: return@run null
            val duration = prefs[LOCKOUT_DURATION] ?: return@run null
            Lockout(boot, endsAt, duration)
        }
        return PinState(credential, prefs[FAILED_ATTEMPTS] ?: 0, lockout)
    }

    override suspend fun write(state: PinState) {
        // One edit, so the PIN and its attempt count are always saved together.
        dataStore.edit { prefs ->
            val credential = state.credential
            if (credential == null) {
                prefs.remove(SALT)
                prefs.remove(HASH)
                prefs.remove(ITERATIONS)
            } else {
                prefs[SALT] = encode(credential.salt)
                prefs[HASH] = encode(credential.hash)
                prefs[ITERATIONS] = credential.iterations
            }
            prefs[FAILED_ATTEMPTS] = state.failedAttempts
            val lockout = state.lockout
            if (lockout == null) {
                prefs.remove(LOCKOUT_BOOT)
                prefs.remove(LOCKOUT_ENDS_AT)
                prefs.remove(LOCKOUT_DURATION)
            } else {
                prefs[LOCKOUT_BOOT] = lockout.bootCount
                prefs[LOCKOUT_ENDS_AT] = lockout.endsAtSinceBootMillis
                prefs[LOCKOUT_DURATION] = lockout.durationMillis
            }
        }
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(text: String) = Base64.decode(text, Base64.NO_WRAP)

    private companion object {
        val SALT = stringPreferencesKey("salt")
        val HASH = stringPreferencesKey("hash")
        val ITERATIONS = intPreferencesKey("iterations")
        val FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")
        val LOCKOUT_BOOT = intPreferencesKey("lockout_boot_count")
        val LOCKOUT_ENDS_AT = longPreferencesKey("lockout_ends_at_since_boot_ms")
        val LOCKOUT_DURATION = longPreferencesKey("lockout_duration_ms")
    }
}
