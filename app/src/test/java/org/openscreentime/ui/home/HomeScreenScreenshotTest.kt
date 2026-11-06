package org.openscreentime.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openscreentime.ui.theme.OpenScreenTimeTheme
import org.robolectric.annotation.Config

/**
 * Screenshot tests let the UI be reviewed without a phone. The reference
 * images are in app/src/test/screenshots.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi") // Pixel-sized phone
class HomeScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeScreen_light() {
        composeRule.setContent {
            OpenScreenTimeTheme(darkTheme = false, dynamicColor = false) { HomeScreen() }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/home_light.png")
    }

    @Test
    @Config(qualifiers = "+night")
    fun homeScreen_dark() {
        composeRule.setContent {
            OpenScreenTimeTheme(darkTheme = true, dynamicColor = false) { HomeScreen() }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/home_dark.png")
    }
}
