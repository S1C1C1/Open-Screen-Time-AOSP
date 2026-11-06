package org.openscreentime.pin

import android.content.ContentResolver
import android.os.SystemClock
import android.provider.Settings
import java.time.Duration

/**
 * Time for PIN waits. Uses time since the phone started, which changing the
 * date, time or time zone doesn't affect, plus a count of restarts so the
 * app can tell when that clock started over.
 */
interface DeviceClock {
    fun sinceBoot(): Duration
    fun bootCount(): Int
}

class SystemDeviceClock(private val contentResolver: ContentResolver) : DeviceClock {
    override fun sinceBoot(): Duration = Duration.ofMillis(SystemClock.elapsedRealtime())

    override fun bootCount(): Int =
        Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, 0)
}
