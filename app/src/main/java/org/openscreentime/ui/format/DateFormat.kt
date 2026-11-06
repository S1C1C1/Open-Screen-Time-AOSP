package org.openscreentime.ui.format

import android.text.format.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A date like "Wednesday, November 4", in the order the user's language expects. */
fun formatDayWithDate(date: LocalDate, locale: Locale): String {
    val pattern = DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd")
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}
