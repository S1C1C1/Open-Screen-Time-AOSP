package org.openscreentime.usage

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/** An app that can be opened from the app drawer. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    /** The category the app's developer chose, e.g. ApplicationInfo.CATEGORY_GAME. */
    val category: Int = ApplicationInfo.CATEGORY_UNDEFINED,
)

/** Lists apps a limit can be set for. An interface so tests can supply their own. */
fun interface InstalledApps {
    fun launchableApps(): List<InstalledApp>
}

/** Apps with an app drawer icon, except this one, sorted by name. */
class PackageManagerInstalledApps(
    private val packageManager: PackageManager,
    private val ownPackage: String,
) : InstalledApps {
    override fun launchableApps(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager
            .queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            .mapNotNull { it.activityInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != ownPackage }
            .map {
                InstalledApp(
                    packageName = it.packageName,
                    label = it.applicationInfo.loadLabel(packageManager).toString(),
                    category = it.applicationInfo.category,
                )
            }
            .sortedBy { it.label.lowercase() }
    }
}
