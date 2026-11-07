package org.openscreentime.allowed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistiveAppsTest {

    @Test
    fun readsPackagesFromAndroidsComponentLists() {
        assertEquals(
            setOf("com.example.keyboard", "org.futo.inputmethod"),
            packagesFromComponentList("com.example.keyboard/.Ime;-921088104:org.futo.inputmethod/org.futo.Ime"),
        )
        assertEquals(setOf("com.example.reader"), packagesFromComponentList("com.example.reader/com.example.reader.Service"))
        assertEquals(emptySet<String>(), packagesFromComponentList(null))
        assertEquals(emptySet<String>(), packagesFromComponentList(""))
    }

    @Test
    fun keyboardsAndAccessibilityAreAlwaysAllowedButNotListed() {
        val policy = AlwaysAllowedPolicy(
            systemApps = FakeSystemApps(phone = "dialer", emergency = "emergency", settings = "settings"),
            ownPackage = "org.openscreentime",
            homeScreenPackages = { emptySet() },
            assistiveApps = FakeAssistiveApps(always = setOf("keyboard", "reader"), atStartup = setOf("keyboard", "reader", "automation")),
        )
        // The keyboard in use and accessibility tools are never blocked...
        assertTrue(policy.isAlwaysAllowed("keyboard", AlwaysAllowedConfig()))
        assertTrue(policy.isAlwaysAllowed("reader", AlwaysAllowedConfig()))
        // ...but an ordinary app with an accessibility service switched on can be.
        assertFalse(policy.isAlwaysAllowed("automation", AlwaysAllowedConfig()))
        assertTrue("automation" in policy.protectedAtStartup())
        assertFalse(policy.isAlwaysAllowed("video", AlwaysAllowedConfig()))
        assertFalse(policy.listed(AlwaysAllowedConfig()).any { it.packageName == "keyboard" })
    }
}

class FakeAssistiveApps(private val always: Set<String> = emptySet(), private val atStartup: Set<String> = always) : AssistiveApps {
    override fun alwaysProtected() = always
    override fun protectedAtStartup() = atStartup
}
