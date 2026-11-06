package org.openscreentime.ui.format

import android.content.res.Resources
import org.openscreentime.R
import java.time.Duration

/** Formats a time like "1 hr 5 min", "45 min" or "Less than 1 min". Rounds down to the minute. */
fun formatDuration(duration: Duration, resources: Resources): String {
    val totalMinutes = duration.toMinutes()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        duration.isZero || duration.isNegative -> resources.getString(R.string.duration_minutes, 0)
        totalMinutes == 0L -> resources.getString(R.string.duration_less_than_minute)
        hours == 0L -> resources.getString(R.string.duration_minutes, minutes)
        minutes == 0L -> resources.getString(R.string.duration_hours, hours)
        else -> resources.getString(R.string.duration_hours_minutes, hours, minutes)
    }
}
