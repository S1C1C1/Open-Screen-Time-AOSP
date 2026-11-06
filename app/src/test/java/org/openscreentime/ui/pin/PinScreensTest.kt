package org.openscreentime.ui.pin

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openscreentime.ui.theme.OpenScreenTimeTheme
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PinScreensTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<String>()
    private val actions = PinFlowActions(
        onDigit = { events += "digit $it" },
        onDelete = { events += "delete" },
        onSubmit = { events += "submit" },
        onContinueIntro = { events += "continue" },
        onWaitFinished = { events += "wait over" },
        onClose = { events += "close" },
    )

    private fun show(ui: PinFlowUi) {
        composeRule.setContent { OpenScreenTimeTheme(dynamicColor = false) { PinFlowScreen(ui, actions) } }
    }

    @Test
    fun numberPadSendsDigitsAndDelete() {
        show(PinFlowUi(PinFlowKind.Verify, PinStep.EnterCurrent, digitCount = 1))
        composeRule.onNodeWithText("4").performClick()
        composeRule.onNodeWithText("0").performClick()
        composeRule.onNodeWithContentDescription("Delete").performClick()
        assertEquals(listOf("digit 4", "digit 0", "delete"), events)
    }

    @Test
    fun okIsOnlyEnabledWithAtLeastFourDigits() {
        var ui by mutableStateOf(PinFlowUi(PinFlowKind.Verify, PinStep.EnterCurrent, digitCount = 3))
        composeRule.setContent { OpenScreenTimeTheme(dynamicColor = false) { PinFlowScreen(ui, actions) } }
        composeRule.onNodeWithText("OK").assertIsNotEnabled()

        ui = ui.copy(digitCount = 4)
        composeRule.onNodeWithText("OK").assertIsEnabled().performClick()
        assertEquals(listOf("submit"), events)
    }

    @Test
    fun dotsAreReadAsACountNeverAsDigits() {
        show(PinFlowUi(PinFlowKind.Verify, PinStep.EnterCurrent, digitCount = 2))
        composeRule.onNodeWithContentDescription("2 digits entered").assertExists()
    }

    @Test
    fun numberPadIsOffDuringAWait() {
        show(PinFlowUi(PinFlowKind.Verify, PinStep.EnterCurrent, waitRemaining = Duration.ofMinutes(5)))
        composeRule.onNodeWithText("Too many wrong attempts. Try again in 5:00").assertExists()
        composeRule.onNodeWithText("7").assertIsNotEnabled()
    }

    @Test
    fun countdownTicksAndReportsWhenTheWaitIsOver() {
        show(PinFlowUi(PinFlowKind.Verify, PinStep.EnterCurrent, waitRemaining = Duration.ofSeconds(3)))
        composeRule.mainClock.advanceTimeBy(1_100)
        composeRule.onNodeWithText("Too many wrong attempts. Try again in 0:02").assertExists()
        composeRule.mainClock.advanceTimeBy(2_100)
        assertTrue("wait over" in events)
    }

    @Test
    fun introShowsTheRequiredWarnings() {
        show(PinFlowUi(PinFlowKind.Setup, PinStep.Intro))
        composeRule.onNodeWithText(
            "If you forget this PIN, the only way to remove it is to factory reset the phone, which erases all data.",
        ).assertExists()
        composeRule.onNodeWithText("This PIN is different from the code you use to unlock your phone", substring = true)
            .assertExists()
        composeRule.onNodeWithText("Continue").performClick()
        assertEquals(listOf("continue"), events)
    }

    @Test
    fun screenshotsAreBlockedOnlyWhilePinScreensAreOpen() {
        var open by mutableStateOf(true)
        composeRule.setContent {
            OpenScreenTimeTheme(dynamicColor = false) {
                if (open) PinFlowScreen(PinFlowUi(PinFlowKind.Verify, PinStep.EnterCurrent), actions)
            }
        }
        fun secure() = composeRule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        assertTrue(secure())

        open = false
        composeRule.waitForIdle()
        assertFalse(secure())
    }
}
