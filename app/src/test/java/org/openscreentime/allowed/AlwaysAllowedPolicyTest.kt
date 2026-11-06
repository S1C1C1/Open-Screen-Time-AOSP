package org.openscreentime.allowed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSystemApps(
    var phone: String? = "com.example.dialer",
    var emergency: String? = "com.android.phone",
    var settings: String? = "com.android.settings",
) : SystemApps {
    override fun defaultPhoneApp() = phone
    override fun emergencyDialer() = emergency
    override fun settingsApp() = settings
}

class AlwaysAllowedPolicyTest {

    private val system = FakeSystemApps()
    private val policy = AlwaysAllowedPolicy(system, ownPackage = "org.openscreentime") { setOf("com.example.launcher") }
    private val defaults = AlwaysAllowedConfig()

    @Test
    fun defaultsArePhoneEmergencyAndSettings() {
        assertEquals(
            listOf(
                AllowedApp("com.example.dialer", AllowedRole.Phone),
                AllowedApp("com.android.phone", AllowedRole.Emergency),
                AllowedApp("com.android.settings", AllowedRole.Settings),
            ),
            policy.listed(defaults),
        )
    }

    @Test
    fun phoneFollowsTheCurrentDefaultPhoneApp() {
        system.phone = "com.example.newdialer"
        assertTrue(policy.isAlwaysAllowed("com.example.newdialer", defaults))
        assertFalse(policy.isAlwaysAllowed("com.example.dialer", defaults))
    }

    @Test
    fun emergencyCallingCannotBeRemoved() {
        val emergency = AllowedApp("com.android.phone", AllowedRole.Emergency)
        assertFalse(emergency.removable)
        val after = policy.remove(emergency, defaults)
        assertEquals(defaults, after)
        assertTrue(policy.isAlwaysAllowed("com.android.phone", after))
    }

    @Test
    fun emergencyStaysEvenWithEverythingElseRemoved() {
        var config = defaults
        policy.listed(config).filter { it.removable }.forEach { config = policy.remove(it, config) }
        assertEquals(listOf(AllowedApp("com.android.phone", AllowedRole.Emergency)), policy.listed(config))
    }

    @Test
    fun phoneAndSettingsCanBeRemovedAndAddedBack() {
        var config = policy.remove(AllowedApp("com.example.dialer", AllowedRole.Phone), defaults)
        config = policy.remove(AllowedApp("com.android.settings", AllowedRole.Settings), config)
        assertFalse(policy.isAlwaysAllowed("com.example.dialer", config))
        assertFalse(policy.isAlwaysAllowed("com.android.settings", config))

        config = policy.add("com.example.dialer", config)
        config = policy.add("com.android.settings", config)
        assertEquals(defaults, config)
    }

    @Test
    fun addedAppsAreListedAfterTheDefaultsInNameOrder() {
        var config = policy.add("com.example.maps", defaults)
        config = policy.add("com.example.books", config)
        assertEquals(
            listOf("com.example.books", "com.example.maps"),
            policy.listed(config).filter { it.role == AllowedRole.Added }.map { it.packageName },
        )
        config = policy.remove(AllowedApp("com.example.maps", AllowedRole.Added), config)
        assertFalse(policy.isAlwaysAllowed("com.example.maps", config))
    }

    @Test
    fun screenTimeAppAndHomeScreenAreAlwaysAllowedButNotListed() {
        assertTrue(policy.isAlwaysAllowed("org.openscreentime", defaults))
        assertTrue(policy.isAlwaysAllowed("com.example.launcher", defaults))
        assertFalse(policy.listed(defaults).any { it.packageName == "org.openscreentime" })
    }

    @Test
    fun otherAppsAreNotAllowed() {
        assertFalse(policy.isAlwaysAllowed("com.example.game", defaults))
    }

    @Test
    fun phoneWithoutTelephonyHasNoPhoneEntry() {
        system.phone = null
        assertEquals(
            listOf(AllowedRole.Emergency, AllowedRole.Settings),
            policy.listed(defaults).map { it.role },
        )
    }

    @Test
    fun appAddedThatIsAlsoTheDefaultIsListedOnce() {
        val config = defaults.copy(addedPackages = setOf("com.android.settings"))
        assertEquals(1, policy.listed(config).count { it.packageName == "com.android.settings" })
    }
}
