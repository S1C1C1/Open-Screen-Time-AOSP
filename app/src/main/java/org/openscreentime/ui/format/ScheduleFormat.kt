package org.openscreentime.ui.format

import android.content.res.Resources
import org.openscreentime.R
import org.openscreentime.schedules.ActiveSchedule
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * "Bedtime on until 6:00 AM", or "… and 1 more" when several are on.
 * Null when no schedule is on. [active] is soonest to end first.
 */
fun formatActiveSchedules(active: List<ActiveSchedule>, resources: Resources, locale: Locale, use24Hour: Boolean): String? {
    val first = active.firstOrNull() ?: return null
    val until = formatTime(first.end.toLocalTime(), locale, use24Hour)
    val more = active.size - 1
    return if (more == 0) {
        resources.getString(R.string.schedules_active, first.name, until)
    } else {
        resources.getQuantityString(R.plurals.schedules_active_more, more, first.name, until, more)
    }
}

/** "Every day", "Monday to Friday", "Saturday and Sunday", or short names like "Mon, Wed, Fri". */
fun formatDays(days: Set<DayOfWeek>, daysInOrder: List<DayOfWeek>, resources: Resources, locale: Locale): String {
    val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    return when (days) {
        DayOfWeek.entries.toSet() -> resources.getString(R.string.schedules_every_day)
        DayOfWeek.entries.toSet() - weekend -> resources.getString(R.string.schedules_weekdays)
        weekend -> resources.getString(R.string.schedules_weekends)
        else -> daysInOrder.filter { it in days }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
    }
}
