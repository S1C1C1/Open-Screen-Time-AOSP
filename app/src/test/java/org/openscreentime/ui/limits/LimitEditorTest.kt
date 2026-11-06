package org.openscreentime.ui.limits

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openscreentime.limits.AppLimit
import org.openscreentime.ui.theme.OpenScreenTimeTheme
import org.openscreentime.usage.InstalledApp
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h1000dp-xxhdpi")
class LimitEditorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var saved: AppLimit? = AppLimit("unset", Duration.ZERO)

    private fun editor(current: AppLimit?) {
        composeRule.setContent {
            OpenScreenTimeTheme(dynamicColor = false) {
                LimitEditorScreen("video", "Video", current, onBack = {}, onSave = { saved = it })
            }
        }
    }

    private fun tap(description: String, times: Int = 1) =
        repeat(times) { composeRule.onNodeWithContentDescription(description).performScrollTo().performClick() }

    @Test
    fun newLimitStartsAtOneHourAndStepsByFifteenMinutes() {
        editor(current = null)
        tap("More: Minutes", times = 3)
        tap("Less: Minutes")
        composeRule.onNodeWithText("Save").performClick()
        assertEquals(AppLimit("video", Duration.ofMinutes(90)), saved)
    }

    @Test
    fun hoursStepByOneHour() {
        editor(current = null)
        tap("More: Hours", times = 2)
        composeRule.onNodeWithText("Save").performClick()
        assertEquals(Duration.ofHours(3), saved!!.weekday)
    }

    @Test
    fun cannotGoBelowFifteenMinutes() {
        editor(current = AppLimit("video", Duration.ofMinutes(15)))
        composeRule.onNodeWithContentDescription("Less: Minutes").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Less: Hours").assertIsNotEnabled()
    }

    @Test
    fun cannotGoAboveTwentyThreeHoursFortyFive() {
        editor(current = AppLimit("video", Duration.ofHours(23).plusMinutes(45)))
        composeRule.onNodeWithContentDescription("More: Minutes").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("More: Hours").assertIsNotEnabled()
    }

    @Test
    fun separateWeekendLimit() {
        editor(current = AppLimit("video", Duration.ofHours(1)))
        composeRule.onNodeWithText("Different limit on weekends").performClick()
        // The weekend stepper is the second one; its buttons come second in the tree.
        composeRule.onNodeWithText("Saturday and Sunday").assertExists()
        composeRule.onAllNodesWithContentDescriptionAt("More: Hours", 1).performScrollTo().performClick()
        composeRule.onNodeWithText("Save").performClick()
        assertEquals(AppLimit("video", Duration.ofHours(1), weekend = Duration.ofHours(2)), saved)
    }

    @Test
    fun switchingOffRemovesTheLimit() {
        editor(current = AppLimit("video", Duration.ofHours(1)))
        composeRule.onNodeWithText("Limit this app").performClick()
        composeRule.onNodeWithText("Save").performClick()
        assertNull(saved)
    }

    @Test
    fun pickerSearchFiltersByName() {
        var picked: String? = null
        composeRule.setContent {
            OpenScreenTimeTheme(dynamicColor = false) {
                AppPickerScreen(
                    apps = listOf(InstalledApp("maps", "Maps"), InstalledApp("mail", "Mail"), InstalledApp("camera", "Camera")),
                    onBack = {},
                    onAppClick = { picked = it },
                )
            }
        }
        composeRule.onNodeWithText("Search apps").performTextInput("ma")
        composeRule.onNodeWithText("Camera").assertDoesNotExist()
        composeRule.onNodeWithText("Mail").performClick()
        assertEquals("mail", picked)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithContentDescriptionAt(
        description: String,
        index: Int,
    ) = onAllNodes(androidx.compose.ui.test.hasContentDescription(description))[index]
}
