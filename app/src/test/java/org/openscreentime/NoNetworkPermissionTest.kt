package org.openscreentime

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the privacy promise: the app must never be able to use the network.
 * This reads the final, merged manifest, so it also catches a permission
 * added by a library.
 */
@RunWith(AndroidJUnit4::class)
class NoNetworkPermissionTest {

    @Test
    fun appDoesNotRequestNetworkPermissions() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val requested = packageInfo.requestedPermissions.orEmpty().toSet()

        for (permission in NETWORK_PERMISSIONS) {
            assertFalse("App must not request $permission", permission in requested)
        }
    }

    private companion object {
        val NETWORK_PERMISSIONS = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_NETWORK_STATE",
        )
    }
}
