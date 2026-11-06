package org.openscreentime.blocking

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openscreentime.TestScreenTimeApplication
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestScreenTimeApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
class BlockActivityTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun showsTheRealBlockScreenAndCloseGoesHome() {
        ActivityScenario.launch<BlockActivity>(BlockActivity.intent(app, "com.example.video", BlockReason.LimitReached)).use { scenario ->
            composeRule.onNodeWithText("Time limit reached").assertExists()
            // A real block screen has no preview banner.
            composeRule.onNodeWithText("Preview", substring = true).assertDoesNotExist()

            composeRule.onNodeWithText("Close app").performClick()
            val home = shadowOf(app).nextStartedActivity
            assertEquals(Intent.ACTION_MAIN, home.action)
            assertTrue(home.hasCategory(Intent.CATEGORY_HOME))
            scenario.onActivity { assertTrue(it.isFinishing) }
        }
    }

    @Test
    fun backAlsoGoesHome() {
        ActivityScenario.launch<BlockActivity>(BlockActivity.intent(app, "com.example.video", BlockReason.LimitReached)).use { scenario ->
            composeRule.onNodeWithText("Time limit reached").assertExists()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            assertTrue(shadowOf(app).nextStartedActivity.hasCategory(Intent.CATEGORY_HOME))
        }
    }

    @Test
    fun downtimeReasonIsShown() {
        ActivityScenario.launch<BlockActivity>(BlockActivity.intent(app, "com.example.video", BlockReason.Downtime)).use {
            composeRule.onNodeWithText("Downtime").assertExists()
        }
    }

    @Test
    fun cannotBeOpenedByOtherAppsAndStaysOutOfRecents() {
        val info = app.packageManager
            .getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
            .activities!!
            .single { it.name == BlockActivity::class.java.name }
        assertFalse(info.exported)
        assertTrue(info.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS != 0)
    }
}
