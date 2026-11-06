package org.openscreentime.blocking

/**
 * Applies blocking to the phone. Kept behind this interface so the same
 * rules and screens work with either enforcement method planned for phase 2:
 * - System app mode: suspending packages with PackageManager, using a
 *   privileged permission granted by the ROM.
 * - Device-owner mode: DevicePolicyManager.setPackagesSuspended.
 */
interface AppBlocker {
    /** Makes exactly [packages] blocked; all other apps are unblocked. */
    suspend fun setBlockedApps(packages: Set<String>)
}

/** Phase 1: a regular app can't block other apps, so this does nothing. Block screens can be previewed instead. */
class PreviewOnlyAppBlocker : AppBlocker {
    override suspend fun setBlockedApps(packages: Set<String>) = Unit
}
