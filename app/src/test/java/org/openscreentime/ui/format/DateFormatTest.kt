package org.openscreentime.ui.format

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class DateFormatTest {
    @Test
    fun formatsDayWithDateForTheLanguage() {
        val date = LocalDate.of(2026, 11, 4)
        assertEquals("Wednesday, November 4", formatDayWithDate(date, Locale.US))
        assertEquals("Mittwoch, 4. November", formatDayWithDate(date, Locale.GERMANY))
    }
}
