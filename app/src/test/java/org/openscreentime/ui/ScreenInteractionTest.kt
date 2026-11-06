package org.openscreentime.ui

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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
                HomeScreen(status = HomeStatus.Ready, onTrackingChange = { tracking = it }, onAllowUsageAccess = {})
            }
        }
        composeRule.onNodeWithText("Use screen time").assertIsOn().performClick()
        assertEquals(false, tracking)
    }

    @Test
    fun homeTurnOnButtonWhenOff() {
        var tracking: Boolean? = null
        composeRule.setContent {
            OpenScreenTimeTheme(dynamicColor = false) {
                HomeScreen(status = HomeStatus.TrackingOff, onTrackingChange = { tracking = it }, onAllowUsageAccess = {})
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
                HomeScreen(status = HomeStatus.NeedsUsageAccess, onTrackingChange = {}, onAllowUsageAccess = { tapped = true })
            }
        }
        composeRule.onNodeWithText("Allow usage access").performClick()
        assertEquals(true, tapped)
    }

}
