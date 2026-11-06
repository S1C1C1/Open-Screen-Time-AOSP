package org.openscreentime.usage

import android.content.pm.PackageManager

/** Turns a package name into the app's name shown to the user. */
fun interface AppLabels {
    fun labelFor(packageName: String): String
}

class PackageManagerAppLabels(private val packageManager: PackageManager) : AppLabels {
    override fun labelFor(packageName: String): String = try {
        val info = packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        packageManager.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        // Uninstalled, or not visible to this app: show the package name.
        packageName
    }
}
