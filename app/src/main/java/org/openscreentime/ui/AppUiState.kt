package org.openscreentime.ui

import org.openscreentime.data.AppSettings

/** Which full screen the app shows. */
enum class AppScreen { Loading, Welcome, UsageAccessGuide, Home }

/** What the home screen tells the user about tracking. */
enum class HomeStatus {
    /** The user chose not to track screen time. */
    TrackingOff,

    /** Tracking is on, but Android isn't letting the app see app usage. */
    NeedsUsageAccess,

    /** Everything is set up. */
    Ready,
}

/** Everything the UI needs to decide what to show. */
data class AppUiState(
    /** Null until the saved settings have been read. */
    val settings: AppSettings? = null,
    val hasUsageAccess: Boolean = false,
    /** True after the user asked to set up usage access, until it's granted or dismissed. */
    val usageAccessGuideOpen: Boolean = false,
) {
    val screen: AppScreen
        get() = when {
            settings == null -> AppScreen.Loading
            !settings.onboardingCompleted -> AppScreen.Welcome
            usageAccessGuideOpen && !hasUsageAccess -> AppScreen.UsageAccessGuide
            else -> AppScreen.Home
        }

    val homeStatus: HomeStatus
        get() = homeStatusFor(
            trackingEnabled = settings?.trackingEnabled == true,
            hasUsageAccess = hasUsageAccess,
        )
}

fun homeStatusFor(trackingEnabled: Boolean, hasUsageAccess: Boolean): HomeStatus = when {
    !trackingEnabled -> HomeStatus.TrackingOff
    !hasUsageAccess -> HomeStatus.NeedsUsageAccess
    else -> HomeStatus.Ready
}
