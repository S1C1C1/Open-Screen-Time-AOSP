package org.openscreentime.ui

import kotlinx.coroutines.flow.MutableStateFlow
import org.openscreentime.allowed.AlwaysAllowedConfig
import org.openscreentime.allowed.AlwaysAllowedPolicy
import org.openscreentime.allowed.AlwaysAllowedRepository
import org.openscreentime.allowed.FakeSystemApps

/** In-memory always allowed choices for tests. */
class FakeAlwaysAllowedRepository : AlwaysAllowedRepository {
    override val config = MutableStateFlow(AlwaysAllowedConfig())
    override suspend fun save(config: AlwaysAllowedConfig) {
        this.config.value = config
    }
}

/** A policy with fixed system apps: Phone, Emergency and Settings. */
fun testAlwaysAllowedPolicy() = AlwaysAllowedPolicy(
    systemApps = FakeSystemApps(phone = "dialer", emergency = "emergency", settings = "settings"),
    ownPackage = "org.openscreentime",
    homeScreenPackages = { setOf("launcher") },
)
