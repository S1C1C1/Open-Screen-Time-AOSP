package org.openscreentime.usage

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.Settings

/** Tells whether the user has allowed this app to see app usage. */
fun interface UsageAccessChecker {
    fun hasUsageAccess(): Boolean
}

/**
 * Checks the "Usage access" switch in Settings. For a regular app this is
 * an app-op the user turns on by hand; a privileged system app (phase 3) can
 * be granted it ahead of time.
 */
class AppOpsUsageAccessChecker(private val context: Context) : UsageAccessChecker {

    override fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        // checkOpNoThrow works on every supported version (Android 17 un-deprecated it).
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            // "Default" means the app-op defers to the normal permission.
            AppOpsManager.MODE_DEFAULT -> context.checkSelfPermission(
                android.Manifest.permission.PACKAGE_USAGE_STATS,
            ) == PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }
}

/** Opens this app's own "Usage access" page in Settings. */
fun usageAccessSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", packageName, null))

/** Fallback: the list of all apps on the "Usage access" page. */
fun usageAccessListIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
