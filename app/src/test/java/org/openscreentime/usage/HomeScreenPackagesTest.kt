package org.openscreentime.usage

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class HomeScreenPackagesTest {

    @Test
    fun findsEveryInstalledHomeScreenApp() {
        val packageManager = ApplicationProvider.getApplicationContext<Context>().packageManager
        val filter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        listOf("com.example.launcher", "com.example.otherlauncher").forEach { pkg ->
            val component = ComponentName(pkg, "$pkg.Home")
            shadowOf(packageManager).addActivityIfNotPresent(component)
            shadowOf(packageManager).addIntentFilterForActivity(component, filter)
        }

        assertEquals(
            setOf("com.example.launcher", "com.example.otherlauncher"),
            allHomeScreenPackages(packageManager),
        )
    }

    @Test
    fun onlyTheHomeScreenInUseIsProtected() {
        val all = setOf("com.example.launcher", "com.example.otherlauncher")
        assertEquals(setOf("com.example.launcher"), homeScreensInUse(all, "com.example.launcher"))
    }

    @Test
    fun withNoDefaultChosenEveryHomeScreenIsProtected() {
        val all = setOf("com.example.launcher", "com.example.otherlauncher")
        // Android answers with its chooser ("android") until a default is chosen.
        assertEquals(all, homeScreensInUse(all, "android"))
        assertEquals(all, homeScreensInUse(all, null))
    }
}
