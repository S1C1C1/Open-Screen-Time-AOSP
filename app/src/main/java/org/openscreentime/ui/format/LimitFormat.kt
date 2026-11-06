package org.openscreentime.ui.format

import android.content.res.Resources
import org.openscreentime.R
import org.openscreentime.limits.AppLimit

/** "1 hr a day", or "1 hr on weekdays, 2 hr on weekends". */
fun formatLimit(limit: AppLimit, resources: Resources): String {
    val weekday = formatDuration(limit.weekday, resources)
    val weekend = limit.weekend
    return if (weekend == null) {
        resources.getString(R.string.limit_every_day, weekday)
    } else {
        resources.getString(R.string.limit_weekday_weekend, weekday, formatDuration(weekend, resources))
    }
}
