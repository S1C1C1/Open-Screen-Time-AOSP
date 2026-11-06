package org.openscreentime.admin

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.openscreentime.ui.FakeDeviceOwner
import org.robolectric.Shadows.shadowOf

/** Records which protections are on. */
class FakeProtectionActions : ProtectionActions {
    private var uninstall = false
    private var userControl = false
    private var notifications = false
    private var safeBoot = false
    val allOn get() = uninstall && userControl && notifications && safeBoot
    val allOff get() = !uninstall && !userControl && !notifications && !safeBoot

    override fun setUninstallBlocked(blocked: Boolean) { uninstall = blocked }
    override fun setUserControlDisabled(disabled: Boolean) { userControl = disabled }
    override fun lockNotificationPermission(locked: Boolean) { notifications = locked }
    override fun setSafeBootBlocked(blocked: Boolean) { safeBoot = blocked }
}

@RunWith(AndroidJUnit4::class)
class SelfProtectionTest {

    private val actions = FakeProtectionActions()
    private val owner = FakeDeviceOwner(EnforcementMode.DeviceOwner)
    private val protection = SelfProtection(owner, actions)

    @Test
    fun everythingIsProtectedOnceAPinIsSet() {
        protection.update(pinSet = true)
        assertTrue(actions.allOn)
    }

    @Test
    fun withoutAPinNothingIsLocked() {
        protection.update(pinSet = true)
        protection.update(pinSet = false)
        assertTrue(actions.allOff)
    }

    @Test
    fun previewModeTouchesNothing() {
        owner.current = EnforcementMode.PreviewOnly
        protection.update(pinSet = true)
        assertTrue(actions.allOff)
    }

    @Test
    fun removeAllUndoesEverything() {
        protection.update(pinSet = true)
        protection.removeAll()
        assertTrue(actions.allOff)
    }

    @Test
    fun devicePolicyActionsSetTheRightThings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val policy = context.getSystemService(DevicePolicyManager::class.java)
        val admin = ScreenTimeAdmin.component(context)
        shadowOf(policy).setDeviceOwner(admin)
        val userManager = context.getSystemService(UserManager::class.java)
        val real = DevicePolicyProtectionActions(context)

        real.setUninstallBlocked(true)
        real.lockNotificationPermission(true)
        real.setSafeBootBlocked(true)
        assertTrue(policy.isUninstallBlocked(admin, context.packageName))
        assertEquals(
            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED,
            policy.getPermissionGrantState(admin, context.packageName, Manifest.permission.POST_NOTIFICATIONS),
        )
        assertTrue(userManager.hasUserRestriction(UserManager.DISALLOW_SAFE_BOOT))

        real.setUninstallBlocked(false)
        real.lockNotificationPermission(false)
        real.setSafeBootBlocked(false)
        assertFalse(policy.isUninstallBlocked(admin, context.packageName))
        assertEquals(
            DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT,
            policy.getPermissionGrantState(admin, context.packageName, Manifest.permission.POST_NOTIFICATIONS),
        )
        assertFalse(userManager.hasUserRestriction(UserManager.DISALLOW_SAFE_BOOT))
        // setUserControlDisabled can't be checked here (Robolectric doesn't simulate it); see the PR's "How to test".
    }
}
