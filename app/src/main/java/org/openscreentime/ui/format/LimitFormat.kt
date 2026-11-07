package org.openscreentime.ui.format

import android.content.res.Resources
import org.openscreentime.R
import org.openscreentime.groups.DailyLimit
import org.openscreentime.limits.AppLimit
import java.time.Duration

/** "1 hr a day", or "1 hr on weekdays, 2 hr on weekends". */
fun formatLimit(limit: AppLimit, resources: Resources): String = formatLimit(limit.weekday, limit.weekend, resources)

/** The same, for a group's shared limit. */
fun formatLimit(limit: DailyLimit, resources: Resources): String = formatLimit(limit.weekday, limit.weekend, resources)

private fun formatLimit(weekdayLimit: Duration, weekendLimit: Duration?, resources: Resources): String {
    val weekday = formatDuration(weekdayLimit, resources)
    return if (weekendLimit == null) {
        resources.getString(R.string.limit_every_day, weekday)
    } else {
        resources.getString(R.string.limit_weekday_weekend, weekday, formatDuration(weekendLimit, resources))
    }
}
