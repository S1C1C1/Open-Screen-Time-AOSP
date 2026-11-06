package org.openscreentime

import android.content.Context
import androidx.room.Room
import org.openscreentime.data.DataStoreSettingsRepository
import org.openscreentime.data.SettingsRepository
import org.openscreentime.data.UsageDatabase
import org.openscreentime.data.settingsDataStore
import org.openscreentime.usage.AppLabels
import org.openscreentime.usage.AppOpsUsageAccessChecker
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

    private val database: UsageDatabase by lazy {
        Room.databaseBuilder(appContext, UsageDatabase::class.java, "usage.db").build()
    }

    val usageRepository: UsageRepository by lazy {
        RoomUsageRepository(
            dao = database.dailyUsageDao(),
            eventSource = SystemUsageEventSource(appContext),
            // Checked on each sync, in case the user switches home screen apps.
            excludedPackages = { homeScreenPackages(appContext.packageManager) },
        )
    }
}
