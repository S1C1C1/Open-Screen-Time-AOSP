package org.openscreentime.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openscreentime.data.HistoryLength
import org.openscreentime.ui.appdetail.AppDetailScreen
import org.openscreentime.ui.dashboard.Dashboard
import org.openscreentime.ui.dashboard.buildAppDetail
import org.openscreentime.ui.home.HistoryLengthDialog
import org.openscreentime.ui.home.HomeScreen
import org.openscreentime.ui.theme.OpenScreenTimeTheme
import org.openscreentime.ui.usageaccess.UsageAccessScreen
import org.openscreentime.ui.welcome.WelcomeScreen
import org.robolectric.annotation.Config

/**
 * Screenshot tests let the UI be reviewed without a phone. The reference
 * images are in app/src/test/screenshots; see README for how to update them.
 * The dashboard is captured at full height so everything is visible.
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
    @Config(qualifiers = "w411dp-h1400dp-xxhdpi")
    fun dashboard_light() = capture("dashboard_light") { HomeFor(HomeStatus.Ready) }

    @Test
    @Config(qualifiers = "w411dp-h1400dp-night-xxhdpi")
    fun dashboard_dark() = capture("dashboard_dark", dark = true) { HomeFor(HomeStatus.Ready) }

    @Test
    fun dashboard_earlierDaySelected() = capture("dashboard_earlier_day") {
        HomeFor(HomeStatus.Ready, SampleUsage.dashboard(selectedDay = SampleUsage.today.minusDays(1)))
    }

    @Test
    fun dashboard_noUsageYet() = capture("dashboard_no_usage") {
        HomeFor(HomeStatus.Ready, SampleUsage.dashboard(usage = emptyMap()))
    }

    @Test
    fun appDetail_light() = capture("app_detail_light") { BrowserDetail() }

    @Test
    @Config(qualifiers = "+night")
    fun appDetail_dark() = capture("app_detail_dark", dark = true) { BrowserDetail() }

    @Test
    fun historyDialog() = capture("history_dialog") {
        HistoryLengthDialog(selected = HistoryLength.FourWeeks, onSelect = {}, onDismiss = {})
    }

    @Composable
    private fun HomeFor(status: HomeStatus, dashboard: Dashboard = SampleUsage.dashboard()) {
        HomeScreen(
            status = status,
            dashboard = dashboard,
            historyLength = HistoryLength.FourWeeks,
            onTrackingChange = {},
            onAllowUsageAccess = {},
            onHistoryLengthChange = {},
        )
    }

    @Composable
    private fun BrowserDetail() {
        AppDetailScreen(
            detail = buildAppDetail(
                "browser",
                SampleUsage.usageByDay,
                SampleUsage.today,
                SampleUsage.firstDayOfWeek,
                SampleUsage.labels,
            ),
            onBack = {},
        )
    }
}
