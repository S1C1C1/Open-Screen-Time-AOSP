package org.openscreentime.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class DeviceOwnerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val policy = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(context, ScreenTimeAdmin::class.java)

    @Test
    fun aNormalInstallIsPreviewOnly() {
        assertEquals(EnforcementMode.PreviewOnly, AndroidDeviceOwner(context).mode())
    }

    @Test
    fun afterSetupItIsTheDeviceOwner() {
        shadowOf(policy).setDeviceOwner(admin)
        assertEquals(EnforcementMode.DeviceOwner, AndroidDeviceOwner(context).mode())
    }

    // Removing a device owner can't be checked here: Robolectric doesn't
    // simulate clearDeviceOwnerApp. It is checked on a real phone (see the
    // pull request's "How to test"), and MainViewModelTest checks the PIN step.

    @Test
    fun removingWhenNotDeviceOwnerDoesNothing() {
        AndroidDeviceOwner(context).remove()
        assertEquals(EnforcementMode.PreviewOnly, AndroidDeviceOwner(context).mode())
    }

    @Test
    fun onlyTheSystemCanTalkToTheAdminComponent() {
        // Without this permission, any app could send fake admin events.
        val receiver = context.packageManager.getReceiverInfo(admin, PackageManager.ComponentInfoFlags.of(0))
        assertEquals("android.permission.BIND_DEVICE_ADMIN", receiver.permission)
    }

    @Test
    fun setupCommandMatchesTheAdminComponent() {
        assertTrue(SET_DEVICE_OWNER_COMMAND.endsWith("${context.packageName}/.admin.ScreenTimeAdmin"))
        assertEquals("org.openscreentime.admin.ScreenTimeAdmin", admin.className)
    }
}
