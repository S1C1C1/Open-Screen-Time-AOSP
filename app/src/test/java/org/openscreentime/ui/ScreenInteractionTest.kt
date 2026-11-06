package org.openscreentime.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import org.openscreentime.data.HistoryLength
import org.openscreentime.ui.home.HomeScreen
import org.openscreentime.ui.theme.OpenScreenTimeTheme
import org.openscreentime.ui.usageaccess.UsageAccessScreen
import org.openscreentime.ui.welcome.WelcomeScreen

/** Checks that each button does what it says. */
@RunWith(AndroidJUnit4::class)
class ScreenInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun welcomeButtons() {
        val taps = mutableListOf<String>()
        composeRule.setContent {
            OpenScreenTimeTheme(dynamicColor = false) {
                WelcomeScreen(onTurnOn = { taps += "on" }, onNotNow = { taps += "not now" })
            }
        }
        composeRule.onNodeWithText("Turn on screen time").performClick()
        composeRule.onNodeWithText("Not now").performClick()
        assertEquals(listOf("on", "not now"), taps)
    }

    @Test
    fun usageAccessButtons() {
        val taps = mutableListOf<String>()
        composeRule.setContent {
            OpenScreenTimeTheme(dynamicColor = false) {
                UsageAccessScreen(onOpenSettings = { taps += "settings" }, onBack = { taps += "back" })
            }
        }
        composeRule.onNodeWithText("Open settings").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("settings", "back"), taps)
    }

    @Test
    fun homeSwitchTurnsTrackingOff() {
        var tracking: Boolean? = null
        composeRule.setContent {
            OpenScreenTimeTheme(dynamicColor = false) {
                TestHome(HomeStatus.Ready, onTrackingChange = { tracking = it })
            }
        }
        composeRule.onNodeWithText("Use screen time").performScrollTo().assertIsOn().performClick()
        assertEquals(false, tracking)
    }

    @Test
    fun homeTurnOnButtonWhenOff() {
        var tracking: Boolean? = null
        composeRule.setContent {
            OpenScreenTimeTheme(dynamicColor = false) {
                TestHome(HomeStatus.TrackingOff, onTrackingChange = { tracking = it })
            }
        }
        composeRule.onNodeWithText("Use screen time").assertIsOff()
        composeRule.onNodeWithText("Turn on").performClick()
        assertEquals(true, tracking)
    }

    @Test
    fun homeAllowAccessButton() {
        var tapped = false
        composeRule.setContent {
            OpenScreenTimeTheme(dynamicColor = false) {
                TestHome(HomeStatus.NeedsUsageAccess, onAllowUsageAccess = { tapped = true })
            }
        }
        composeRule.onNodeWithText("Allow usage access").performClick()
        assertEquals(true, tapped)
    }


    @Test
    fun tappingABarSelectsThatDay() {
        var selected: LocalDate? = null
        composeRule.setContent {
            OpenScreenTimeTheme(dynamicColor = false) {
                TestHome(HomeStatus.Ready, onSelectDay = { selected = it })
            }
        }
        composeRule.onNodeWithContentDescription("Wednesday, 3 hr 20 min").performClick()
        assertEquals(SampleUsage.today.minusDays(1), selected)
    }

    @Test
    fun futureDaysCannotBeTapped() {
        composeRule.setContent {
            OpenScreenTimeTheme(dynamicColor = false) { TestHome(HomeStatus.Ready) }
        }
        composeRule.onNodeWithContentDescription("Friday").assertHasNoClickAction()
    }

    @Test
    fun tappingAnAppOpensIt() {
        var opened: String? = null
        composeRule.setContent {
            OpenScreenTimeTheme(dynamicColor = false) {
                TestHome(HomeStatus.Ready, onAppClick = { opened = it })
            }
        }
        composeRule.onNodeWithText("Messages").performScrollTo().performClick()
        assertEquals("messages", opened)
    }

    @Test
    fun historySettingOpensDialogAndSaves() {
        var chosen: HistoryLength? = null
        composeRule.setContent {
            OpenScreenTimeTheme(dynamicColor = false) {
                TestHome(HomeStatus.Ready, onHistoryLengthChange = { chosen = it })
            }
        }
        composeRule.onNodeWithText("Keep usage history").performScrollTo().performClick()
        composeRule.onNodeWithText("1 year").performClick()
        assertEquals(HistoryLength.OneYear, chosen)
        // The dialog closes after choosing.
        composeRule.onAllNodesWithText("1 year").assertCountEquals(0)
    }

    @androidx.compose.runtime.Composable
    private fun TestHome(
        status: HomeStatus,
        onTrackingChange: (Boolean) -> Unit = {},
        onAllowUsageAccess: () -> Unit = {},
        onHistoryLengthChange: (HistoryLength) -> Unit = {},
        onSelectDay: (LocalDate) -> Unit = {},
        onAppClick: (String) -> Unit = {},
    ) = HomeScreen(
        status = status,
        dashboard = SampleUsage.dashboard(),
        historyLength = HistoryLength.FourWeeks,
        onTrackingChange = onTrackingChange,
        onAllowUsageAccess = onAllowUsageAccess,
        onHistoryLengthChange = onHistoryLengthChange,
        onSelectDay = onSelectDay,
        onAppClick = onAppClick,
    )
}
