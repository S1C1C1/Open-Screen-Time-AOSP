package org.openscreentime.ui

import org.openscreentime.admin.DeviceOwner
import org.openscreentime.admin.EnforcementMode

/** Device-owner status for tests. */
class FakeDeviceOwner(var current: EnforcementMode = EnforcementMode.PreviewOnly) : DeviceOwner {
    override fun mode() = current
    override fun remove() {
        current = EnforcementMode.PreviewOnly
    }
}
