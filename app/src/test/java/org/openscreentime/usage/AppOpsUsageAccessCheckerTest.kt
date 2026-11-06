package org.openscreentime.usage

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class AppOpsUsageAccessCheckerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val appOps = context.getSystemService(AppOpsManager::class.java)
    private val checker = AppOpsUsageAccessChecker(context)

    private fun setMode(mode: Int) {
        shadowOf(appOps).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            mode,
        )
    }

    @Test
    fun allowedMeansAccess() {
        setMode(AppOpsManager.MODE_ALLOWED)
        assertTrue(checker.hasUsageAccess())
    }

    @Test
    fun ignoredOrErroredMeansNoAccess() {
        setMode(AppOpsManager.MODE_IGNORED)
        assertFalse(checker.hasUsageAccess())
        setMode(AppOpsManager.MODE_ERRORED)
        assertFalse(checker.hasUsageAccess())
    }

    @Test
    fun settingsIntentOpensThisAppsPage() {
        val intent = usageAccessSettingsIntent("org.openscreentime")
        assertEquals(Settings.ACTION_USAGE_ACCESS_SETTINGS, intent.action)
        assertEquals("package:org.openscreentime", intent.dataString)
    }
}
