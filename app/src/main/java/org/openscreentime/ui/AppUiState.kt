package org.openscreentime.ui

import org.openscreentime.data.AppSettings
import org.openscreentime.data.HistoryLength
import org.openscreentime.usage.AppLabels
import org.openscreentime.usage.AppUsageTime
import java.time.Duration

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
    /** Today's saved usage, or null before it has been read. */
    val todayUsage: TodayUsage? = null,
) {
    val historyLength: HistoryLength
        get() = settings?.historyLength ?: HistoryLength.Default

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

/** One app's time, with the name shown to the user. */
data class AppTime(val label: String, val time: Duration)

/** Today's total and most used apps. */
data class TodayUsage(val total: Duration, val topApps: List<AppTime>) {
    companion object {
        const val TOP_APP_COUNT = 5

        fun from(apps: List<AppUsageTime>, labels: AppLabels): TodayUsage = TodayUsage(
            total = apps.fold(Duration.ZERO) { sum, app -> sum + app.time },
            topApps = apps
                .sortedByDescending { it.time }
                .take(TOP_APP_COUNT)
                .map { AppTime(labels.labelFor(it.packageName), it.time) },
        )
    }
}
