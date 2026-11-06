package org.openscreentime

/** The real app setup, minus background job scheduling, for tests that need the app's shared objects. */
class TestScreenTimeApplication : OpenScreenTimeApplication() {
    override fun scheduleBackgroundWork() = Unit
}
