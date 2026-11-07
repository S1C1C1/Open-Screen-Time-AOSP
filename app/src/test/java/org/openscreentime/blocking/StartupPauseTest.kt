package org.openscreentime.blocking

import org.junit.Assert.assertEquals
import org.junit.Test
import org.openscreentime.allowed.AlwaysAllowedConfig
import org.openscreentime.allowed.AlwaysAllowedPolicy
import org.openscreentime.allowed.FakeSystemApps

class StartupPauseTest {

    private val launchable = setOf("video", "maps", "dialer", "settings", "launcher", "keyboard", "reader", "org.openscreentime")
    private val policy = AlwaysAllowedPolicy(
        systemApps = FakeSystemApps(phone = "dialer", emergency = "emergency", settings = "settings"),
        ownPackage = "org.openscreentime",
        homeScreenPackages = { setOf("launcher") },
        assistiveApps = { setOf("keyboard", "reader") },
    )

    private fun pause(deviceOwner: Boolean) = StartupPause(
        isDeviceOwner = { deviceOwner },
        launchablePackages = { launchable },
        isProtected = { policy.isAlwaysAllowed(it, AlwaysAllowedConfig()) },
    )

    @Test
    fun pausesEverythingExceptProtectedApps() {
        // Phone, Settings, home screen, keyboard, screen reader and this app stay usable.
        assertEquals(setOf("video", "maps"), pause(deviceOwner = true).appsToPause())
    }

    @Test
    fun handsOverEveryLaunchableAppAfterTheUnlock() {
        assertEquals(launchable, pause(deviceOwner = true).appsToHandOver())
    }

    @Test
    fun doesNothingWithoutDeviceOwner() {
        assertEquals(emptySet<String>(), pause(deviceOwner = false).appsToPause())
        assertEquals(emptySet<String>(), pause(deviceOwner = false).appsToHandOver())
    }
}
