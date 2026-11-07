package org.openscreentime.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.time.Instant

/** Where usage events come from. An interface so tests can supply their own. */
fun interface UsageEventSource {
    fun events(start: Instant, end: Instant): List<UsageEvent>
}

/** Reads usage events recorded by Android. Needs usage access. */
class SystemUsageEventSource(context: Context) : UsageEventSource {

    private val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)

    override fun events(start: Instant, end: Instant): List<UsageEvent> {
        // Returns nothing (not an error) if usage access is off.
        val raw = usageStatsManager.queryEvents(start.toEpochMilli(), end.toEpochMilli())
            ?: return emptyList()
        val result = mutableListOf<UsageEvent>()
        val event = UsageEvents.Event()
        while (raw.getNextEvent(event)) {
            toUsageEvent(event.eventType, event.timeStamp, event.packageName, event.className)
                ?.let(result::add)
        }
        return result
    }

    companion object {
        /** Maps an Android event to ours, or null for events that don't matter here. */
        fun toUsageEvent(type: Int, timeMillis: Long, packageName: String?, className: String?): UsageEvent? {
            val time = Instant.ofEpochMilli(timeMillis)
            val pkg = packageName.orEmpty()
            val cls = className.orEmpty()
            return when (type) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageEvent.AppResumed(time, pkg, cls)
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED,
                -> UsageEvent.AppPaused(time, pkg, cls)
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.KEYGUARD_SHOWN,
                UsageEvents.Event.DEVICE_SHUTDOWN,
                -> UsageEvent.ScreenOff(time)
                else -> null
            }
        }
    }
}

/**
 * The home screen app (launcher) in use. Its time isn't counted and it's
 * never blocked. Other apps that can act as a home screen are treated
 * like any other app, so a second launcher-style app can't escape limits.
 *
 * Until the user has chosen a default home screen, Android has no single
 * answer, so every home screen app is returned and none can be blocked.
 */
fun homeScreenPackages(packageManager: PackageManager): Set<String> {
    val all = allHomeScreenPackages(packageManager)
    val current = packageManager
        .resolveActivity(HOME_INTENT, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        ?.activityInfo?.packageName
    return homeScreensInUse(all, current)
}

/**
 * [current] if it's one of [all]; otherwise (no default chosen, so Android
 * answered with its chooser, which isn't a home screen app) all of them.
 */
fun homeScreensInUse(all: Set<String>, current: String?): Set<String> =
    if (current != null && current in all) setOf(current) else all

private val HOME_INTENT = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

/**
 * Every installed home screen app. Visible to this app through the
 * <queries> entry in the manifest.
 */
fun allHomeScreenPackages(packageManager: PackageManager): Set<String> {
    val homeIntent = HOME_INTENT
    return packageManager
        // Include home screens that can't run before the first unlock, so the
        // startup pause never pauses the user's chosen home screen.
        .queryIntentActivities(
            homeIntent,
            PackageManager.ResolveInfoFlags.of(
                PackageManager.MATCH_DIRECT_BOOT_AWARE.toLong() or PackageManager.MATCH_DIRECT_BOOT_UNAWARE.toLong(),
            ),
        )
        .mapNotNull { it.activityInfo?.packageName }
        .toSet()
}
