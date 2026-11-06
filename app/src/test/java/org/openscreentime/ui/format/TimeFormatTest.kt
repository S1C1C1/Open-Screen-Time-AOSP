package org.openscreentime.ui.format

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.openscreentime.downtime.DowntimeStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class TimeFormatTest {

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val today = LocalDate.of(2026, 11, 5) // Thursday
    private fun at(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(ZoneOffset.UTC)

    /** Android puts a narrow no-break space before AM/PM; compare with plain spaces. */
    private fun plain(text: String) = text.replace(' ', ' ').replace(' ', ' ')

    private fun status(s: DowntimeStatus) =
        plain(formatDowntimeStatus(s, today, resources, Locale.US, use24Hour = false))

    @Test
    fun timesFollowTheTwelveOrTwentyFourHourSetting() {
        val time = LocalTime.of(21, 5)
        assertEquals("9:05 PM", plain(formatTime(time, Locale.US, use24Hour = false)))
        assertEquals("21:05", formatTime(time, Locale.US, use24Hour = true))
    }

    @Test
    fun statusTexts() {
        assertEquals("Off", status(DowntimeStatus.Off))
        assertEquals("On now, until 7:00 AM", status(DowntimeStatus.ActiveUntil(at(today.plusDays(1), 7))))
        assertEquals("Starts today at 9:00 PM", status(DowntimeStatus.StartsAt(at(today, 21))))
        assertEquals("Starts tomorrow at 9:00 PM", status(DowntimeStatus.StartsAt(at(today.plusDays(1), 21))))
        assertEquals("Starts Saturday at 11:00 PM", status(DowntimeStatus.StartsAt(at(today.plusDays(2), 23))))
    }
}
