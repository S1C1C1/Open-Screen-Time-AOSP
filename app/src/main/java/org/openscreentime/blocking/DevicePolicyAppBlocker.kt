package org.openscreentime.blocking

import android.app.admin.DevicePolicyManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openscreentime.R
import org.openscreentime.admin.DeviceOwner
import org.openscreentime.admin.EnforcementMode
import org.openscreentime.admin.ScreenTimeAdmin

val Context.pausedAppsDataStore: DataStore<Preferences> by preferencesDataStore(name = "paused_apps")

/**
 * Device-owner mode: pauses apps with DevicePolicyManager.setPackagesSuspended.
 * A paused app can't be opened; Android shows a short message instead,
 * which points the user to Screen time. Remembers which apps it paused, so
 * it only ever unpauses its own.
 */
class DevicePolicyAppBlocker(
    private val context: Context,
    private val dataStore: DataStore<Preferences> = context.pausedAppsDataStore,
) : AppBlocker {

    private val policy = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = ScreenTimeAdmin.component(context)

    /**
     * One change at a time. Without this, handing over the apps paused at
     * startup could happen in the middle of a normal check and be
     * overwritten, leaving those apps paused until the next restart.
     */
    private val mutex = Mutex()

    suspend fun pausedApps(): Set<String> = dataStore.data.map { it[PAUSED] ?: emptySet() }.first()

    override suspend fun setBlockedApps(packages: Set<String>): Unit = mutex.withLock {
        val before = pausedApps()
        val toPause = packages - before
        val toUnpause = before - packages
        if (toPause.isEmpty() && toUnpause.isEmpty()) return@withLock

        var failed = emptySet<String>()
        if (toPause.isNotEmpty()) {
            policy.setShortSupportMessage(admin, context.getString(R.string.block_support_message))
            // Android refuses some apps (for example the dialer or this app); those stay unpaused.
            failed = policy.setPackagesSuspended(admin, toPause.toTypedArray(), true).toSet()
        }
        if (toUnpause.isNotEmpty()) policy.setPackagesSuspended(admin, toUnpause.toTypedArray(), false)

        dataStore.edit { it[PAUSED] = packages - failed }
    }

    /**
     * Pauses [packages] as the phone starts. Before the first unlock this
     * app's saved data can't be read or written, so nothing is recorded
     * here; [handOver] records them after the unlock.
     */
    fun pauseBeforeUnlock(packages: Set<String>) {
        if (packages.isEmpty()) return
        policy.setShortSupportMessage(admin, context.getString(R.string.block_support_message))
        policy.setPackagesSuspended(admin, packages.toTypedArray(), true)
    }

    /**
     * After the unlock: counts [packages] as paused by this app, so the next
     * normal check unpauses the ones that should be allowed.
     */
    suspend fun handOver(packages: Set<String>) {
        if (packages.isEmpty()) return
        mutex.withLock { dataStore.edit { it[PAUSED] = (it[PAUSED] ?: emptySet()) + packages } }
    }

    private companion object {
        val PAUSED = stringSetPreferencesKey("paused")
    }
}

/** Uses device-owner pausing when the app is device owner; otherwise does nothing (preview only). */
class ModeAwareAppBlocker(
    private val deviceOwner: DeviceOwner,
    private val devicePolicy: AppBlocker,
) : AppBlocker {
    override suspend fun setBlockedApps(packages: Set<String>) {
        if (deviceOwner.mode() == EnforcementMode.DeviceOwner) devicePolicy.setBlockedApps(packages)
    }
}
