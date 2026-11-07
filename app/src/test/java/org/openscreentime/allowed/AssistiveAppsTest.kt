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
            assistiveApps = { setOf("keyboard") },
        )
        assertTrue(policy.isAlwaysAllowed("keyboard", AlwaysAllowedConfig()))
        assertFalse(policy.isAlwaysAllowed("video", AlwaysAllowedConfig()))
        assertFalse(policy.listed(AlwaysAllowedConfig()).any { it.packageName == "keyboard" })
    }
}
