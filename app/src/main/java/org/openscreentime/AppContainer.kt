package org.openscreentime

import android.content.Context
import androidx.room.Room
import org.openscreentime.data.DataStoreSettingsRepository
import org.openscreentime.data.SettingsRepository
import org.openscreentime.data.UsageDatabase
import org.openscreentime.data.settingsDataStore
import org.openscreentime.admin.AndroidDeviceOwner
import org.openscreentime.admin.DeviceOwner
import org.openscreentime.admin.DevicePolicyProtectionActions
import org.openscreentime.admin.SelfProtection
import org.openscreentime.allowed.AlwaysAllowedPolicy
import org.openscreentime.allowed.AlwaysAllowedRepository
import org.openscreentime.allowed.AndroidSystemApps
import org.openscreentime.allowed.DataStoreAlwaysAllowedRepository
import org.openscreentime.allowed.alwaysAllowedDataStore
import org.openscreentime.blocking.AppBlocker
import org.openscreentime.monitor.DataStoreShownWarningsStore
import org.openscreentime.monitor.ShownWarningsStore
import org.openscreentime.monitor.shownWarningsDataStore
import org.openscreentime.blocking.DataStoreOverrideRepository
import org.openscreentime.blocking.OverrideRepository
import org.openscreentime.blocking.DevicePolicyAppBlocker
import org.openscreentime.blocking.ModeAwareAppBlocker
import org.openscreentime.blocking.overrideDataStore
import org.openscreentime.downtime.DataStoreDowntimeRepository
import org.openscreentime.downtime.DowntimeRepository
import org.openscreentime.downtime.downtimeDataStore
import org.openscreentime.limits.LimitRepository
import org.openscreentime.limits.RoomLimitRepository
import org.openscreentime.pin.DataStorePinStore
import org.openscreentime.pin.DeviceClock
import org.openscreentime.restrictions.DataStoreRestrictionsRepository
import org.openscreentime.restrictions.DevicePolicyRestrictionApplier
import org.openscreentime.restrictions.ModeAwareRestrictionApplier
import org.openscreentime.restrictions.RestrictionApplier
import org.openscreentime.restrictions.RestrictionsRepository
import org.openscreentime.restrictions.restrictionsDataStore
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

    /** Time since phone start, which changing the date doesn't affect. */
    val deviceClock: DeviceClock = SystemDeviceClock(appContext.contentResolver)

    val restrictionsRepository: RestrictionsRepository =
        DataStoreRestrictionsRepository(appContext.restrictionsDataStore)

    /** Created on first use: making the keystore key can take a moment. */
    val pinManager: PinManager by lazy {
        PinManager(
            store = DataStorePinStore(appContext.pinDataStore),
            hasher = PinHasher(KeystoreMac(appContext.packageManager)),
            clock = deviceClock,
        )
    }

    val limitRepository: LimitRepository by lazy { RoomLimitRepository(database.appLimitDao()) }

    val downtimeRepository: DowntimeRepository = DataStoreDowntimeRepository(appContext.downtimeDataStore)

    val alwaysAllowedRepository: AlwaysAllowedRepository =
        DataStoreAlwaysAllowedRepository(appContext.alwaysAllowedDataStore)

    val alwaysAllowedPolicy = AlwaysAllowedPolicy(
        systemApps = AndroidSystemApps(appContext),
        ownPackage = appContext.packageName,
        homeScreenPackages = { homeScreenPackages(appContext.packageManager) },
    )

    val overrideRepository: OverrideRepository = DataStoreOverrideRepository(appContext.overrideDataStore)

    val deviceOwner: DeviceOwner = AndroidDeviceOwner(appContext)

    val selfProtection = SelfProtection(deviceOwner, DevicePolicyProtectionActions(appContext))

    val restrictionApplier: RestrictionApplier =
        ModeAwareRestrictionApplier(deviceOwner, DevicePolicyRestrictionApplier(appContext))

    val shownWarningsStore: ShownWarningsStore = DataStoreShownWarningsStore(appContext.shownWarningsDataStore)

    /** Pauses apps when this app is the device owner; otherwise blocking can only be previewed. */
    val appBlocker: AppBlocker = ModeAwareAppBlocker(deviceOwner, DevicePolicyAppBlocker(appContext))

    val usageEventSource = SystemUsageEventSource(appContext)

    val installedApps: InstalledApps = PackageManagerInstalledApps(appContext.packageManager, appContext.packageName)

    val usageRepository: UsageRepository by lazy {
        RoomUsageRepository(
            dao = database.dailyUsageDao(),
            eventSource = usageEventSource,
            // Checked on each sync, in case the user switches home screen apps.
            excludedPackages = { homeScreenPackages(appContext.packageManager) },
        )
    }
}
