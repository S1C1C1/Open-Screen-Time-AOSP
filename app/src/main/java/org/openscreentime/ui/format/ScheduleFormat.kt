package org.openscreentime.ui.format

import android.content.res.Resources
import org.openscreentime.R
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

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
