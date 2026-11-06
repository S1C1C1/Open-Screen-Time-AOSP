package org.openscreentime.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openscreentime.ui.home.HomeScreen
import org.openscreentime.ui.theme.OpenScreenTimeTheme
import org.openscreentime.ui.usageaccess.UsageAccessScreen
import org.openscreentime.ui.welcome.WelcomeScreen
import org.robolectric.annotation.Config

/**
 * Screenshot tests let the UI be reviewed without a phone. The reference
 * images are in app/src/test/screenshots; see README for how to update them.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi") // Pixel-sized phone
class ScreenshotTests {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        composeRule.setContent {
            OpenScreenTimeTheme(darkTheme = dark, dynamicColor = false, content = content)
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun welcome_light() = capture("welcome_light") { WelcomeScreen(onTurnOn = {}, onNotNow = {}) }

    @Test
    @Config(qualifiers = "+night")
    fun welcome_dark() = capture("welcome_dark", dark = true) { WelcomeScreen(onTurnOn = {}, onNotNow = {}) }

    @Test
    fun usageAccess_light() = capture("usage_access_light") {
        UsageAccessScreen(onOpenSettings = {}, onBack = {})
    }

    @Test
    @Config(qualifiers = "+night")
    fun usageAccess_dark() = capture("usage_access_dark", dark = true) {
        UsageAccessScreen(onOpenSettings = {}, onBack = {})
    }

    @Test
    fun home_trackingOff() = capture("home_tracking_off") { HomeFor(HomeStatus.TrackingOff) }

    @Test
    fun home_needsUsageAccess() = capture("home_needs_usage_access") { HomeFor(HomeStatus.NeedsUsageAccess) }

    @Test
    fun home_ready_light() = capture("home_ready_light") { HomeFor(HomeStatus.Ready) }

    @Test
    @Config(qualifiers = "+night")
    fun home_ready_dark() = capture("home_ready_dark", dark = true) { HomeFor(HomeStatus.Ready) }

    @Composable
    private fun HomeFor(status: HomeStatus) {
        HomeScreen(status = status, onTrackingChange = {}, onAllowUsageAccess = {})
    }
}
