package org.openscreentime.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openscreentime.data.AppSettings
import org.openscreentime.pin.testPinManager
import org.openscreentime.usage.InstalledApp
import org.openscreentime.ui.theme.OpenScreenTimeTheme
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.ZoneOffset

/** Moving from the dashboard to an app's details and back, with the real app wiring. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi") // Pixel-sized phone
class NavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun startApp(): MainViewModel {
        val usage = FakeUsageRepository().apply { usageByDay.value = SampleUsage.usageByDay }
        val viewModel = MainViewModel(
            settingsRepository = FakeSettingsRepository(AppSettings(onboardingCompleted = true, trackingEnabled = true)),
            usageAccessChecker = { true },
            usageRepository = usage,
            appLabels = SampleUsage.labels,
            pinManager = testPinManager(),
            limitRepository = FakeLimitRepository(),
            installedApps = { listOf(InstalledApp("video", "Video"), InstalledApp("maps", "Maps")) },
            clock = Clock.fixed(SampleUsage.today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
            firstDayOfWeek = SampleUsage.firstDayOfWeek,
        )
        composeRule.setContent {
            OpenScreenTimeTheme(dynamicColor = false) {
                OpenScreenTimeApp(viewModel = viewModel, onOpenUsageAccessSettings = {})
            }
        }
        return viewModel
    }

    @Test
    fun openAppDetailAndGoBack() {
        startApp()
        composeRule.onNodeWithText("Messages").performScrollTo().performClick()
        composeRule.onNodeWithText("App limit").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Daily average this week: 16 min").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Keep usage history").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun addALimitFromSettings() {
        startApp()
        composeRule.onNodeWithText("App limits").performScrollTo().performClick()
        composeRule.onNodeWithText("Set a PIN so only you can change these limits.").assertIsDisplayed()

        composeRule.onNodeWithText("Add app limit").performClick()
        composeRule.onNodeWithText("Maps").performClick()
        composeRule.onNodeWithText("Every day").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("More: Minutes").performScrollTo().performClick()
        composeRule.onNodeWithText("Save").performClick()

        // Back on the list, which now shows the new limit.
        composeRule.onNodeWithText("1 hr 15 min a day").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("1 app").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun setALimitFromAnAppsPage() {
        startApp()
        composeRule.onNodeWithText("Maps").performScrollTo().performClick()
        composeRule.onNodeWithText("App limit").performScrollTo().performClick()
        composeRule.onNodeWithText("Save").performClick()

        // Back on the app's page: Maps was used 9 min today, so 51 min of the 1 hr default are left.
        composeRule.onNodeWithText("1 hr a day").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("51 min left today").assertIsDisplayed()
    }
}
