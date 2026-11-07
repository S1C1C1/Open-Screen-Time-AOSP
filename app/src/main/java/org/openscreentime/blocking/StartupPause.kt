package org.openscreentime.blocking

import android.content.Intent
import android.content.pm.PackageManager

/**
 * Closes the short gap after a restart.
 *
 * Android keeps paused apps paused through a restart, but this app's
 * saved rules are locked until the first unlock. A block that should have
 * started while the phone was off (or extra time that ran out) would only
 * take effect once the app has read its rules again.
 *
 * So, as soon as the phone starts (before the first unlock), every app
 * except the protected ones is paused. After the unlock the background
 * service reads the rules and unpauses whatever is allowed, within a few
 * seconds. Nothing is saved in the storage that can be read before the
 * first unlock, in line with GrapheneOS keeping data locked until then;
 * the cost is that every app is briefly paused after each restart.
 */
class StartupPause(
    private val isDeviceOwner: () -> Boolean,
    private val launchablePackages: () -> Set<String>,
    /** Apps that must never be paused: phone, emergency calling, Settings, home screen, keyboards, accessibility. */
    private val protectedPackages: () -> Set<String>,
) {
    /** Apps to pause as the phone starts. Empty unless this app is the device owner. */
    fun appsToPause(): Set<String> =
        if (isDeviceOwner()) launchablePackages() - protectedPackages() else emptySet()

    /**
     * Apps that may have been paused at startup, to hand over to normal
     * enforcement after the unlock. All launchable apps are included, not
     * only [appsToPause], in case the protected list looked different
     * before the unlock; unpausing an app that wasn't paused does nothing.
     */
    fun appsToHandOver(): Set<String> = if (isDeviceOwner()) launchablePackages() else emptySet()
}

/**
 * Every app with an app drawer icon, including ones that can't run before
 * the first unlock (which Android otherwise leaves out at that point).
 */
fun allLaunchablePackages(packageManager: PackageManager): Set<String> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val flags = PackageManager.MATCH_DIRECT_BOOT_AWARE.toLong() or PackageManager.MATCH_DIRECT_BOOT_UNAWARE.toLong()
    return packageManager
        .queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags))
        .mapNotNull { it.activityInfo?.packageName }
        .toSet()
}
