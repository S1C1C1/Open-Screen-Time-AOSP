package org.openscreentime.restrictions

import android.app.admin.DevicePolicyManager
import android.content.Context
import org.openscreentime.admin.DeviceOwner
import org.openscreentime.admin.EnforcementMode
import org.openscreentime.admin.ScreenTimeAdmin

/** Puts restrictions in force on the phone. Behind an interface so system app mode can use another way (phase 3). */
fun interface RestrictionApplier {
    /** Makes exactly [active] restrictions apply; the other managed ones are cleared. */
    fun apply(active: Set<Restriction>)
}

/** Device-owner mode: Android user restrictions set with DevicePolicyManager. */
class DevicePolicyRestrictionApplier(context: Context) : RestrictionApplier {
    private val policy = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = ScreenTimeAdmin.component(context)

    override fun apply(active: Set<Restriction>) {
        for (restriction in Restriction.entries) {
            for (key in restriction.userRestrictions) {
                if (restriction in active) policy.addUserRestriction(admin, key) else policy.clearUserRestriction(admin, key)
            }
        }
    }
}

/** Applies restrictions only when this app is the device owner. */
class ModeAwareRestrictionApplier(
    private val deviceOwner: DeviceOwner,
    private val devicePolicy: RestrictionApplier,
) : RestrictionApplier {
    override fun apply(active: Set<Restriction>) {
        if (deviceOwner.mode() == EnforcementMode.DeviceOwner) devicePolicy.apply(active)
    }
}
