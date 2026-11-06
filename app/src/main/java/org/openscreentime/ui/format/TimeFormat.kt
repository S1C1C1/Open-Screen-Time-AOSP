package org.openscreentime.ui.format

import android.content.res.Resources
import android.text.format.DateFormat
import org.openscreentime.R
import org.openscreentime.downtime.DowntimeStatus
import org.openscreentime.downtime.DowntimeWindow
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** A time like "9:00 PM" or "21:00", following the phone's 12/24-hour setting. */
fun formatTime(time: LocalTime, locale: Locale, use24Hour: Boolean): String {
    val pattern = DateFormat.getBestDateTimePattern(locale, if (use24Hour) "Hm" else "hm")
    return DateTimeFormatter.ofPattern(pattern, locale).format(time)
}

/** "9:00 PM – 7:00 AM". */
fun formatWindow(window: DowntimeWindow, resources: Resources, locale: Locale, use24Hour: Boolean): String =
    resources.getString(
        R.string.downtime_window,
        formatTime(window.start, locale, use24Hour),
        formatTime(window.end, locale, use24Hour),
    )

/** "On now, until 7:00 AM", "Starts today at 9:00 PM", "Starts Saturday at 11:00 PM" or "Off". */
fun formatDowntimeStatus(
    status: DowntimeStatus,
    today: LocalDate,
    resources: Resources,
    locale: Locale,
    use24Hour: Boolean,
): String = when (status) {
    DowntimeStatus.Off -> resources.getString(R.string.downtime_status_off)
    is DowntimeStatus.ActiveUntil -> {
        val time = formatTime(status.end.toLocalTime(), locale, use24Hour)
        when (status.end.toLocalDate()) {
            today, today.plusDays(1) -> resources.getString(R.string.downtime_status_active, time)
            else -> resources.getString(
                R.string.downtime_status_active_day,
                status.end.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
                time,
            )
        }
    }
    is DowntimeStatus.StartsAt -> {
        val time = formatTime(status.start.toLocalTime(), locale, use24Hour)
        when (status.start.toLocalDate()) {
            today -> resources.getString(R.string.downtime_status_today, time)
            today.plusDays(1) -> resources.getString(R.string.downtime_status_tomorrow, time)
            else -> resources.getString(
                R.string.downtime_status_day,
                status.start.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
                time,
            )
        }
    }
}
