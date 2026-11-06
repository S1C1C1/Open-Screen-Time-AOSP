package org.openscreentime.admin

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.UserManager

/** The device-owner powers used to protect this app. Kept separate so the decision logic can be tested. */
interface ProtectionActions {
    /** Stops the app being uninstalled. */
    fun setUninstallBlocked(blocked: Boolean)

    /** Stops the user force-stopping the app or clearing its data in Settings. */
    fun setUserControlDisabled(disabled: Boolean)

    /** Keeps the notification permission granted, so warnings can't be switched off. */
    fun lockNotificationPermission(locked: Boolean)

    /** Stops starting the phone in safe mode, which would switch this app off. */
    fun setSafeBootBlocked(blocked: Boolean)
}

/**
 * Self-protection from the spec, always on once a PIN is set (device-owner
 * mode). Without a PIN nothing is locked, so it's all undone.
 */
class SelfProtection(
    private val deviceOwner: DeviceOwner,
    private val actions: ProtectionActions,
) {
    /** Applies protection when [pinSet], removes it otherwise. Does nothing unless this app is the device owner. */
    fun update(pinSet: Boolean) {
        if (deviceOwner.mode() != EnforcementMode.DeviceOwner) return
        set(pinSet)
    }

    /** Undoes all protection; used before giving up device-owner control. */
    fun removeAll() {
        if (deviceOwner.mode() == EnforcementMode.DeviceOwner) set(false)
    }

    private fun set(on: Boolean) {
        actions.setUninstallBlocked(on)
        actions.setUserControlDisabled(on)
        actions.lockNotificationPermission(on)
        actions.setSafeBootBlocked(on)
    }
}

class DevicePolicyProtectionActions(private val context: Context) : ProtectionActions {
    private val policy = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = ScreenTimeAdmin.component(context)
    private val ownPackage = context.packageName

    override fun setUninstallBlocked(blocked: Boolean) = policy.setUninstallBlocked(admin, ownPackage, blocked)

    override fun setUserControlDisabled(disabled: Boolean) =
        policy.setUserControlDisabledPackages(admin, if (disabled) listOf(ownPackage) else emptyList())

    override fun lockNotificationPermission(locked: Boolean) {
        policy.setPermissionGrantState(
            admin,
            ownPackage,
            Manifest.permission.POST_NOTIFICATIONS,
            if (locked) DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED else DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT,
        )
    }

    override fun setSafeBootBlocked(blocked: Boolean) {
        if (blocked) {
            policy.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
        } else {
            policy.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
        }
    }
}
