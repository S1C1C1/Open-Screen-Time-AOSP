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
 * Packages whose time isn't counted: every installed home screen app
 * (launcher). Visible to this app through the <queries> entry in the manifest.
 */
fun homeScreenPackages(packageManager: PackageManager): Set<String> {
    val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    return packageManager
        .queryIntentActivities(homeIntent, PackageManager.ResolveInfoFlags.of(0))
        .mapNotNull { it.activityInfo?.packageName }
        .toSet()
}
