package org.openscreentime.ui

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.openscreentime.data.AppSettings
import org.robolectric.Shadows.shadowOf

// Robolectric provides Android's main thread, which the ViewModel runs on.
@RunWith(AndroidJUnit4::class)
class MainViewModelTest {

    private var accessGranted = false
    private val settings = FakeSettingsRepository()

    private fun newViewModel() = MainViewModel(settings) { accessGranted }

    private fun MainViewModel.currentState(): AppUiState {
        shadowOf(Looper.getMainLooper()).idle()
        return uiState.value
    }

    @Test
    fun firstLaunchShowsWelcome() {
        assertEquals(AppScreen.Welcome, newViewModel().currentState().screen)
    }

    @Test
    fun turnOnWithoutAccessShowsGuide() {
        val viewModel = newViewModel()
        viewModel.onWelcomeTurnOn()

        val state = viewModel.currentState()
        assertEquals(AppSettings(onboardingCompleted = true, trackingEnabled = true), state.settings)
        assertEquals(AppScreen.UsageAccessGuide, state.screen)
    }

    @Test
    fun turnOnWithAccessAlreadyGrantedGoesHome() {
        accessGranted = true
        val viewModel = newViewModel()
        viewModel.onWelcomeTurnOn()

        val state = viewModel.currentState()
        assertEquals(AppScreen.Home, state.screen)
        assertEquals(HomeStatus.Ready, state.homeStatus)
    }

    @Test
    fun notNowGoesHomeWithTrackingOff() {
        val viewModel = newViewModel()
        viewModel.onWelcomeNotNow()

        val state = viewModel.currentState()
        assertEquals(AppScreen.Home, state.screen)
        assertEquals(HomeStatus.TrackingOff, state.homeStatus)
    }

    @Test
    fun grantingAccessInSettingsClosesGuide() {
        val viewModel = newViewModel()
        viewModel.onWelcomeTurnOn()
        assertEquals(AppScreen.UsageAccessGuide, viewModel.currentState().screen)

        // The user grants access in Settings and comes back to the app.
        accessGranted = true
        viewModel.refreshUsageAccess()

        val state = viewModel.currentState()
        assertEquals(AppScreen.Home, state.screen)
        assertEquals(HomeStatus.Ready, state.homeStatus)
    }

    @Test
    fun guideStaysClosedIfAccessIsLaterRevoked() {
        accessGranted = true
        val viewModel = newViewModel()
        viewModel.onWelcomeTurnOn()

        accessGranted = false
        viewModel.refreshUsageAccess()

        // Home warns about it instead of jumping back to the guide.
        val state = viewModel.currentState()
        assertEquals(AppScreen.Home, state.screen)
        assertEquals(HomeStatus.NeedsUsageAccess, state.homeStatus)
    }

    @Test
    fun backFromGuideShowsHomeWarning() {
        val viewModel = newViewModel()
        viewModel.onWelcomeTurnOn()
        viewModel.closeUsageAccessGuide()

        val state = viewModel.currentState()
        assertEquals(AppScreen.Home, state.screen)
        assertEquals(HomeStatus.NeedsUsageAccess, state.homeStatus)
    }

    @Test
    fun allowAccessButtonReopensGuide() {
        val viewModel = newViewModel()
        viewModel.onWelcomeTurnOn()
        viewModel.closeUsageAccessGuide()
        viewModel.openUsageAccessGuide()

        assertEquals(AppScreen.UsageAccessGuide, viewModel.currentState().screen)
    }

    @Test
    fun turningTrackingOffAndOnFromHome() {
        accessGranted = true
        val viewModel = newViewModel()
        viewModel.onWelcomeNotNow()

        viewModel.setTrackingEnabled(true)
        assertEquals(HomeStatus.Ready, viewModel.currentState().homeStatus)

        viewModel.setTrackingEnabled(false)
        assertEquals(HomeStatus.TrackingOff, viewModel.currentState().homeStatus)
    }

    @Test
    fun turningTrackingOnWithoutAccessShowsGuide() {
        val viewModel = newViewModel()
        viewModel.onWelcomeNotNow()
        viewModel.setTrackingEnabled(true)

        assertEquals(AppScreen.UsageAccessGuide, viewModel.currentState().screen)
    }
}
