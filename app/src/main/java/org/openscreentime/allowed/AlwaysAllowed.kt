package org.openscreentime.allowed

/**
 * What the user chose to always allow. Phone and Settings are kept as
 * switches rather than package names, so they follow whichever app fills
 * that role now (for example a newly chosen default phone app).
 */
data class AlwaysAllowedConfig(
    val includePhone: Boolean = true,
    val includeSettings: Boolean = true,
    val addedPackages: Set<String> = emptySet(),
)

/** Why an app is on the always allowed list. */
enum class AllowedRole {
    /** The current default phone app. */
    Phone,

    /** Emergency calling. Always allowed and can't be removed. */
    Emergency,

    /** The Settings app. */
    Settings,

    /** Added by the user. */
    Added,
}

data class AllowedApp(val packageName: String, val role: AllowedRole) {
    val removable: Boolean get() = role != AllowedRole.Emergency
}

/** Looks up the apps that fill system roles on this phone. Null when a phone has none. */
interface SystemApps {
    fun defaultPhoneApp(): String?
    fun emergencyDialer(): String?
    fun settingsApp(): String?
}

/**
 * Decides which apps are never blocked by limits or downtime: the user's
 * list, emergency calling (always), this screen time app (so the PIN can
 * always be entered) and the home screen.
 */
class AlwaysAllowedPolicy(
    private val systemApps: SystemApps,
    private val ownPackage: String,
    private val homeScreenPackages: () -> Set<String>,
) {
    /** The list shown to the user, in a fixed order: Phone, Emergency, Settings, then added apps. */
    fun listed(config: AlwaysAllowedConfig): List<AllowedApp> {
        val result = mutableListOf<AllowedApp>()
        if (config.includePhone) systemApps.defaultPhoneApp()?.let { result += AllowedApp(it, AllowedRole.Phone) }
        systemApps.emergencyDialer()?.let { result += AllowedApp(it, AllowedRole.Emergency) }
        if (config.includeSettings) systemApps.settingsApp()?.let { result += AllowedApp(it, AllowedRole.Settings) }
        val shown = result.map { it.packageName }.toMutableSet()
        config.addedPackages.sorted().forEach { pkg ->
            if (shown.add(pkg)) result += AllowedApp(pkg, AllowedRole.Added)
        }
        return result
    }

    fun isAlwaysAllowed(packageName: String, config: AlwaysAllowedConfig): Boolean =
        packageName == ownPackage ||
            packageName in homeScreenPackages() ||
            listed(config).any { it.packageName == packageName }

    /** The config after removing [app]. Emergency calling can't be removed, so it is ignored. */
    fun remove(app: AllowedApp, config: AlwaysAllowedConfig): AlwaysAllowedConfig = when (app.role) {
        AllowedRole.Phone -> config.copy(includePhone = false)
        AllowedRole.Settings -> config.copy(includeSettings = false)
        AllowedRole.Added -> config.copy(addedPackages = config.addedPackages - app.packageName)
        AllowedRole.Emergency -> config
    }

    /** The config after adding [packageName]. Adding back the phone or Settings app restores that role. */
    fun add(packageName: String, config: AlwaysAllowedConfig): AlwaysAllowedConfig = when (packageName) {
        systemApps.defaultPhoneApp() -> config.copy(includePhone = true)
        systemApps.settingsApp() -> config.copy(includeSettings = true)
        else -> config.copy(addedPackages = config.addedPackages + packageName)
    }
}
