package org.openscreentime.admin

import android.app.admin.DevicePolicyManager
import android.content.Context

/** How blocking works on this phone right now. */
enum class EnforcementMode {
    /** A regular app: block screens can only be previewed. */
    PreviewOnly,

    /** Set up as the device owner: apps can really be blocked. */
    DeviceOwner,
}

/** Device-owner status and removal. An interface so tests can use a fake. */
interface DeviceOwner {
    fun mode(): EnforcementMode

    /**
     * Gives up device-owner control so the app can be uninstalled. Android
     * also removes everything set as device owner, such as paused apps and
     * restrictions. Callers must check the PIN first.
     */
    fun remove()
}

class AndroidDeviceOwner(private val context: Context) : DeviceOwner {
    private val policy = context.getSystemService(DevicePolicyManager::class.java)

    override fun mode(): EnforcementMode =
        if (policy.isDeviceOwnerApp(context.packageName)) EnforcementMode.DeviceOwner else EnforcementMode.PreviewOnly

    override fun remove() {
        if (mode() == EnforcementMode.DeviceOwner) {
            @Suppress("DEPRECATION") // Still the supported way for a device owner to remove itself.
            policy.clearDeviceOwnerApp(context.packageName)
        }
    }
}

/** The command that makes this app the device owner. Shown in the app and in docs/device-owner-setup.md. */
const val SET_DEVICE_OWNER_COMMAND = "adb shell dpm set-device-owner org.openscreentime/.admin.ScreenTimeAdmin"
