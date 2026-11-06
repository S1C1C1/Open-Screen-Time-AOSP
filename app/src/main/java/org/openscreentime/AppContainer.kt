package org.openscreentime

import android.content.Context
import androidx.room.Room
import org.openscreentime.data.DataStoreSettingsRepository
import org.openscreentime.data.SettingsRepository
import org.openscreentime.data.UsageDatabase
import org.openscreentime.data.settingsDataStore
import org.openscreentime.downtime.DataStoreDowntimeRepository
import org.openscreentime.downtime.DowntimeRepository
import org.openscreentime.downtime.downtimeDataStore
import org.openscreentime.limits.LimitRepository
import org.openscreentime.limits.RoomLimitRepository
import org.openscreentime.pin.DataStorePinStore
import org.openscreentime.pin.KeystoreMac
import org.openscreentime.pin.PinHasher
import org.openscreentime.pin.PinManager
import org.openscreentime.pin.SystemDeviceClock
import org.openscreentime.pin.pinDataStore
import org.openscreentime.usage.AppIcons
import org.openscreentime.usage.AppLabels
import org.openscreentime.usage.AppOpsUsageAccessChecker
import org.openscreentime.usage.InstalledApps
import org.openscreentime.usage.PackageManagerAppIcons
import org.openscreentime.usage.PackageManagerInstalledApps
import org.openscreentime.usage.PackageManagerAppLabels
import org.openscreentime.usage.RoomUsageRepository
import org.openscreentime.usage.SystemUsageEventSource
import org.openscreentime.usage.UsageAccessChecker
import org.openscreentime.usage.UsageRepository
import org.openscreentime.usage.homeScreenPackages

/** Creates the app's shared objects once, so every screen and job uses the same ones. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settingsRepository: SettingsRepository =
        DataStoreSettingsRepository(appContext.settingsDataStore)

    val usageAccessChecker: UsageAccessChecker = AppOpsUsageAccessChecker(appContext)

    val appLabels: AppLabels = PackageManagerAppLabels(appContext.packageManager)

    val appIcons: AppIcons = PackageManagerAppIcons(appContext.packageManager)

    private val database: UsageDatabase by lazy {
        Room.databaseBuilder(appContext, UsageDatabase::class.java, "usage.db").build()
    }

    /** Created on first use: making the keystore key can take a moment. */
    val pinManager: PinManager by lazy {
        PinManager(
            store = DataStorePinStore(appContext.pinDataStore),
            hasher = PinHasher(KeystoreMac(appContext.packageManager)),
            clock = SystemDeviceClock(appContext.contentResolver),
        )
    }

    val limitRepository: LimitRepository by lazy { RoomLimitRepository(database.appLimitDao()) }

    val downtimeRepository: DowntimeRepository = DataStoreDowntimeRepository(appContext.downtimeDataStore)

    val installedApps: InstalledApps = PackageManagerInstalledApps(appContext.packageManager, appContext.packageName)

    val usageRepository: UsageRepository by lazy {
        RoomUsageRepository(
            dao = database.dailyUsageDao(),
            eventSource = SystemUsageEventSource(appContext),
            // Checked on each sync, in case the user switches home screen apps.
            excludedPackages = { homeScreenPackages(appContext.packageManager) },
        )
    }
}
