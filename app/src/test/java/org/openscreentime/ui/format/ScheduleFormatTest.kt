package org.openscreentime.ui.format

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openscreentime.schedules.ActiveSchedule
import java.time.ZoneOffset
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ScheduleFormatTest {

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val sundayFirst = (0L until 7L).map { DayOfWeek.SUNDAY.plus(it) }
    private fun format(vararg days: DayOfWeek) = formatDays(days.toSet(), sundayFirst, resources, Locale.US)

    @Test
    fun commonDaySetsHaveNames() {
        assertEquals("Every day", format(*DayOfWeek.entries.toTypedArray()))
        assertEquals(
            "Monday to Friday",
            format(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
        )
        assertEquals("Saturday and Sunday", format(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
    }

    @Test
    fun otherDaysAreListedInTheRegionsOrder() {
        assertEquals("Sun, Wed, Fri", format(DayOfWeek.FRIDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY))
    }

    @Test
    fun activeSchedulesSummary() {
        val end = java.time.LocalDate.of(2026, 11, 6).atTime(6, 0).atZone(ZoneOffset.UTC)
        assertNull(formatActiveSchedules(emptyList(), resources, Locale.US, use24Hour = false))
        assertEquals(
            "Bedtime on until 6:00 AM",
            formatActiveSchedules(listOf(ActiveSchedule("Bedtime", end)), resources, Locale.US, use24Hour = false),
        )
        assertEquals(
            "Bedtime on until 6:00 AM, and 1 more",
            formatActiveSchedules(
                listOf(ActiveSchedule("Bedtime", end), ActiveSchedule("Study", end.plusHours(2))),
                resources,
                Locale.US,
                use24Hour = false,
            ),
        )
    }
}
