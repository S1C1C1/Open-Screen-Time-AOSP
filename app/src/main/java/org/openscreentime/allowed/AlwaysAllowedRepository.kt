package org.openscreentime.allowed

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The saved always allowed choices. An interface so tests can use a fake. */
interface AlwaysAllowedRepository {
    val config: Flow<AlwaysAllowedConfig>
    suspend fun save(config: AlwaysAllowedConfig)
}

val Context.alwaysAllowedDataStore: DataStore<Preferences> by preferencesDataStore(name = "always_allowed")

class DataStoreAlwaysAllowedRepository(private val dataStore: DataStore<Preferences>) : AlwaysAllowedRepository {

    override val config: Flow<AlwaysAllowedConfig> = dataStore.data.map { prefs ->
        AlwaysAllowedConfig(
            includePhone = prefs[INCLUDE_PHONE] ?: true,
            includeSettings = prefs[INCLUDE_SETTINGS] ?: true,
            addedPackages = prefs[ADDED] ?: emptySet(),
        )
    }

    override suspend fun save(config: AlwaysAllowedConfig) {
        dataStore.edit { prefs ->
            prefs[INCLUDE_PHONE] = config.includePhone
            prefs[INCLUDE_SETTINGS] = config.includeSettings
            prefs[ADDED] = config.addedPackages
        }
    }

    private companion object {
        val INCLUDE_PHONE = booleanPreferencesKey("include_phone")
        val INCLUDE_SETTINGS = booleanPreferencesKey("include_settings")
        val ADDED = stringSetPreferencesKey("added_packages")
    }
}

/** Finds system role apps with standard Android APIs. Checked each time, so changes are followed. */
class AndroidSystemApps(private val context: Context) : SystemApps {

    override fun defaultPhoneApp(): String? =
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage

    override fun emergencyDialer(): String? =
        resolve(Intent(EMERGENCY_DIAL_ACTION)) ?: EMERGENCY_FALLBACK_PACKAGE.takeIf(::isInstalled)

    override fun settingsApp(): String? = resolve(Intent(Settings.ACTION_SETTINGS))

    /**
     * Only system apps count. Otherwise an installed app could claim to be
     * the emergency dialer or Settings and so make itself always allowed.
     */
    private fun resolve(intent: Intent): String? =
        context.packageManager
            .resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_SYSTEM_ONLY.toLong()))
            ?.activityInfo?.packageName

    private fun isInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private companion object {
        /** AOSP's emergency dialer action; only system apps can handle it. */
        const val EMERGENCY_DIAL_ACTION = "com.android.phone.EmergencyDialer.DIAL"

        /** In AOSP the emergency dialer lives in the system phone package. */
        const val EMERGENCY_FALLBACK_PACKAGE = "com.android.phone"
    }
}
