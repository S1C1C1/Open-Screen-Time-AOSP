package org.openscreentime.ui

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.openscreentime.data.AppSettings
import org.openscreentime.data.HistoryLength
import org.openscreentime.usage.AppUsageTime
import org.robolectric.Shadows.shadowOf
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Robolectric provides Android's main thread, which the ViewModel runs on.
@RunWith(AndroidJUnit4::class)
class MainViewModelTest {

    private var accessGranted = false
    private val settings = FakeSettingsRepository()
    private val usage = FakeUsageRepository()
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 5)
    private val clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), zone)

    private fun newViewModel() = MainViewModel(
        settingsRepository = settings,
        usageAccessChecker = { accessGranted },
        usageRepository = usage,
        appLabels = { pkg -> "Label of $pkg" },
        clock = clock,
    )

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

    @Test
    fun showsTodaysUsageWithAppNames() {
        usage.usageByDay.value = mapOf(
            today to listOf(
                AppUsageTime("mail", Duration.ofMinutes(20)),
                AppUsageTime("chat", Duration.ofMinutes(45)),
            ),
            today.minusDays(1) to listOf(AppUsageTime("old", Duration.ofHours(3))),
        )
        val state = newViewModel().currentState()

        assertEquals(Duration.ofMinutes(65), state.todayUsage!!.total)
        assertEquals(
            listOf(AppTime("Label of chat", Duration.ofMinutes(45)), AppTime("Label of mail", Duration.ofMinutes(20))),
            state.todayUsage.topApps,
        )
    }

    @Test
    fun syncsWhenAppBecomesVisibleWithTrackingAndAccess() {
        accessGranted = true
        val viewModel = newViewModel()
        viewModel.onWelcomeTurnOn()
        viewModel.currentState()
        val before = usage.syncCount

        viewModel.onAppVisible()
        viewModel.currentState()
        assertEquals(before + 1, usage.syncCount)
    }

    @Test
    fun doesNotSyncWhenTrackingIsOff() {
        accessGranted = true
        val viewModel = newViewModel()
        viewModel.onWelcomeNotNow()
        viewModel.onAppVisible()
        viewModel.currentState()
        assertEquals(0, usage.syncCount)
    }

    @Test
    fun doesNotSyncWithoutUsageAccess() {
        val viewModel = newViewModel()
        viewModel.onWelcomeTurnOn()
        viewModel.onAppVisible()
        viewModel.currentState()
        assertEquals(0, usage.syncCount)
    }

    @Test
    fun changingHistoryLengthSavesItAndDeletesOldDays() {
        val viewModel = newViewModel()
        viewModel.setHistoryLength(HistoryLength.OneWeek)

        assertEquals(HistoryLength.OneWeek, viewModel.currentState().historyLength)
        assertEquals(listOf(HistoryLength.OneWeek), usage.deletedWith)
    }

    @Test
    fun historyLengthDefaultsToFourWeeks() {
        assertEquals(HistoryLength.FourWeeks, newViewModel().currentState().historyLength)
    }
}
