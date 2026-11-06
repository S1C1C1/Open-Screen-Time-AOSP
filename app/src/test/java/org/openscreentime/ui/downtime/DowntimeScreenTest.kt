package org.openscreentime.ui.downtime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openscreentime.downtime.DowntimeSchedule
import org.openscreentime.downtime.DowntimeStatus
import org.openscreentime.downtime.DowntimeWindow
import org.openscreentime.ui.theme.OpenScreenTimeTheme
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h1300dp-xxhdpi")
class DowntimeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var schedule by mutableStateOf(DowntimeSchedule())

    private fun show() {
        composeRule.setContent {
            OpenScreenTimeTheme(dynamicColor = false) {
                DowntimeScreen(
                    schedule = schedule,
                    status = DowntimeStatus.Off,
                    today = LocalDate.of(2026, 11, 5),
                    daysInOrder = (0L until 7L).map { DayOfWeek.SUNDAY.plus(it) },
                    pinSet = true,
                    onChange = { schedule = it },
                    onBack = {},
                    onSetPinClick = {},
                )
            }
        }
    }

    @Test
    fun switchTurnsDowntimeOn() {
        show()
        composeRule.onNodeWithText("Use downtime").performClick()
        assertTrue(schedule.enabled)
    }

    @Test
    fun customDaysLetsADayBeTurnedOff() {
        schedule = DowntimeSchedule(enabled = true)
        show()
        composeRule.onNodeWithText("Custom days").performClick()
        assertFalse(schedule.sameEveryDay)

        composeRule.onNodeWithText("Sunday").performScrollTo().performClick()
        assertNull(schedule.windowFor(DayOfWeek.SUNDAY))
        assertEquals(DowntimeWindow.DEFAULT, schedule.windowFor(DayOfWeek.MONDAY))
    }

    @Test
    fun choosingATimeChangesEveryDay() {
        schedule = DowntimeSchedule(enabled = true)
        show()
        composeRule.onNodeWithText("Starts").performClick()
        // The picker opens at 9:00 PM; switching to AM gives 9:00 AM.
        composeRule.onNodeWithText("AM").performClick()
        composeRule.onNodeWithText("OK").performClick()
        DayOfWeek.entries.forEach { day ->
            assertEquals(LocalTime.of(9, 0), schedule.windowFor(day)!!.start)
        }
    }

    @Test
    fun backToEveryDayUsesTheFirstDaysTimes() {
        val custom = DowntimeWindow(LocalTime.of(22, 30), LocalTime.of(6, 0))
        schedule = DowntimeSchedule(enabled = true, sameEveryDay = false)
            .withDay(DayOfWeek.SUNDAY, custom)
            .withDay(DayOfWeek.MONDAY, null)
        show()
        composeRule.onAllNodesWithText("Every day")[0].performClick()
        assertTrue(schedule.sameEveryDay)
        DayOfWeek.entries.forEach { assertEquals(custom, schedule.windowFor(it)) }
    }
}
