package org.openscreentime.usage

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class InstalledAppsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val packageManager = context.packageManager

    private fun install(pkg: String, label: String, vararg activities: String) {
        val appInfo = ApplicationInfo().apply {
            packageName = pkg
            nonLocalizedLabel = label
        }
        shadowOf(packageManager).installPackage(PackageInfo().apply { packageName = pkg; applicationInfo = appInfo })
        val filter = IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        activities.forEach { activity ->
            val component = ComponentName(pkg, "$pkg.$activity")
            shadowOf(packageManager).addActivityIfNotPresent(component)
            shadowOf(packageManager).addIntentFilterForActivity(component, filter)
        }
    }

    @Test
    fun listsLaunchableAppsByNameOnceEachWithoutThisApp() {
        install("com.example.zebra", "Zebra", "Main")
        install("com.example.apple", "apple", "Main", "Second")
        install(context.packageName, "Screen time", "Main")

        val apps = PackageManagerInstalledApps(packageManager, context.packageName).launchableApps()
        assertEquals(listOf("com.example.apple", "com.example.zebra"), apps.map { it.packageName })
    }
}
