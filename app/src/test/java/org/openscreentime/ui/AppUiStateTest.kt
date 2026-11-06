package org.openscreentime.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.openscreentime.data.AppSettings

class AppUiStateTest {

    @Test
    fun showsLoadingUntilSettingsAreRead() {
        assertEquals(AppScreen.Loading, AppUiState(settings = null).screen)
    }

    @Test
    fun showsWelcomeOnFirstLaunch() {
        val state = AppUiState(settings = AppSettings(onboardingCompleted = false))
        assertEquals(AppScreen.Welcome, state.screen)
    }

    @Test
    fun welcomeComesFirstEvenIfGuideWasRequested() {
        val state = AppUiState(
            settings = AppSettings(onboardingCompleted = false),
            usageAccessGuideOpen = true,
        )
        assertEquals(AppScreen.Welcome, state.screen)
    }

    @Test
    fun showsGuideWhenRequestedAndAccessMissing() {
        val state = AppUiState(
            settings = AppSettings(onboardingCompleted = true, trackingEnabled = true),
            hasUsageAccess = false,
            usageAccessGuideOpen = true,
        )
        assertEquals(AppScreen.UsageAccessGuide, state.screen)
    }

    @Test
    fun skipsGuideOnceAccessIsGranted() {
        val state = AppUiState(
            settings = AppSettings(onboardingCompleted = true, trackingEnabled = true),
            hasUsageAccess = true,
            usageAccessGuideOpen = true,
        )
        assertEquals(AppScreen.Home, state.screen)
    }

    @Test
    fun showsHomeAfterOnboarding() {
        val state = AppUiState(settings = AppSettings(onboardingCompleted = true))
        assertEquals(AppScreen.Home, state.screen)
    }

    @Test
    fun homeStatusCoversEveryCombination() {
        assertEquals(HomeStatus.TrackingOff, homeStatusFor(trackingEnabled = false, hasUsageAccess = false))
        assertEquals(HomeStatus.TrackingOff, homeStatusFor(trackingEnabled = false, hasUsageAccess = true))
        assertEquals(HomeStatus.NeedsUsageAccess, homeStatusFor(trackingEnabled = true, hasUsageAccess = false))
        assertEquals(HomeStatus.Ready, homeStatusFor(trackingEnabled = true, hasUsageAccess = true))
    }

    @Test
    fun homeStatusIsOffBeforeSettingsLoad() {
        assertEquals(HomeStatus.TrackingOff, AppUiState(settings = null, hasUsageAccess = true).homeStatus)
    }
}
