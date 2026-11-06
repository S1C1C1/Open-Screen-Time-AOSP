package org.openscreentime.ui

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.openscreentime.data.AppSettings
import org.openscreentime.data.HistoryLength
import org.openscreentime.downtime.DowntimeSchedule
import org.openscreentime.downtime.DowntimeStatus
import org.openscreentime.limits.AppLimit
import org.openscreentime.pin.pin
import org.openscreentime.usage.InstalledApp
import org.openscreentime.pin.testPinManager
import org.openscreentime.ui.pin.PinFlowKind
import org.openscreentime.ui.pin.PinStep
import org.openscreentime.usage.AppUsageTime
import org.robolectric.Shadows.shadowOf
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
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

    private val pinManager = testPinManager()
    private val limits = FakeLimitRepository()
    private val downtime = FakeDowntimeRepository()
    private var installed = listOf(
        InstalledApp("chat", "Chat"),
        InstalledApp("mail", "Mail"),
        InstalledApp("video", "Video"),
    )

    private fun newViewModel() = MainViewModel(
        settingsRepository = settings,
        usageAccessChecker = { accessGranted },
        usageRepository = usage,
        appLabels = { pkg -> "Label of $pkg" },
        pinManager = pinManager,
        limitRepository = limits,
        installedApps = { installed },
        downtimeRepository = downtime,
        clock = clock,
        firstDayOfWeek = DayOfWeek.SUNDAY,
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
    fun dashboardShowsTodayWithAppNames() {
        usage.usageByDay.value = mapOf(
            today to listOf(
                AppUsageTime("mail", Duration.ofMinutes(20)),
                AppUsageTime("chat", Duration.ofMinutes(45)),
            ),
            today.minusDays(1) to listOf(AppUsageTime("video", Duration.ofHours(3))),
        )
        val dashboard = newViewModel().currentState().dashboard!!

        assertEquals(today, dashboard.selectedDay)
        assertEquals(Duration.ofMinutes(65), dashboard.total)
        assertEquals(listOf("Label of chat", "Label of mail"), dashboard.apps.map { it.label })
        // Week of Sunday 1 to Saturday 7 November; yesterday's 3 hours are in it.
        assertEquals(LocalDate.of(2026, 11, 1), dashboard.week.first().date)
        assertEquals(Duration.ofHours(3), dashboard.week.first { it.date == today.minusDays(1) }.time)
    }

    @Test
    fun selectingADayShowsItAndComingBackResetsToToday() {
        usage.usageByDay.value = mapOf(today.minusDays(1) to listOf(AppUsageTime("video", Duration.ofHours(3))))
        val viewModel = newViewModel()

        viewModel.selectDay(today.minusDays(1))
        assertEquals(Duration.ofHours(3), viewModel.currentState().dashboard!!.total)

        viewModel.onAppVisible()
        assertEquals(today, viewModel.currentState().dashboard!!.selectedDay)
    }

    @Test
    fun futureDaysCannotBeSelected() {
        val viewModel = newViewModel()
        viewModel.selectDay(today.plusDays(1))
        assertEquals(today, viewModel.currentState().dashboard!!.selectedDay)
    }

    @Test
    fun appDetailFollowsSavedUsage() {
        usage.usageByDay.value = mapOf(today to listOf(AppUsageTime("chat", Duration.ofMinutes(30))))
        val viewModel = newViewModel()
        var detail: org.openscreentime.ui.dashboard.AppDetail? = null
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate).launch {
            viewModel.appDetail("chat").collect { detail = it }
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Label of chat", detail!!.label)
        assertEquals(Duration.ofMinutes(30), detail.today)

        usage.usageByDay.value = mapOf(today to listOf(AppUsageTime("chat", Duration.ofMinutes(50))))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(Duration.ofMinutes(50), detail.today)
        job.cancel()
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

    // --- Settings protected by the PIN ---

    private fun MainViewModel.enterPin(value: String) {
        value.forEach(pinFlow::addDigit)
        submitPin()
        currentState()
    }

    private fun trackingOnWithPin(): MainViewModel {
        accessGranted = true
        runBlocking { pinManager.setInitialPin(pin("4826")) }
        return newViewModel().also {
            it.onWelcomeTurnOn()
            it.currentState()
        }
    }

    @Test
    fun withoutAPinSettingsChangeStraightAway() {
        accessGranted = true
        val viewModel = newViewModel()
        viewModel.onWelcomeTurnOn()
        viewModel.setTrackingEnabled(false)

        assertEquals(HomeStatus.TrackingOff, viewModel.currentState().homeStatus)
        assertNull(viewModel.pinFlow.ui.value)
    }

    @Test
    fun turningTrackingOffAsksForThePin() {
        val viewModel = trackingOnWithPin()
        viewModel.setTrackingEnabled(false)
        viewModel.currentState()

        assertEquals(PinFlowKind.Verify, viewModel.pinFlow.ui.value!!.kind)
        assertEquals(HomeStatus.Ready, viewModel.currentState().homeStatus)

        viewModel.enterPin("4826")
        assertEquals(HomeStatus.TrackingOff, viewModel.currentState().homeStatus)
    }

    @Test
    fun wrongPinDoesNotChangeTheSetting() {
        val viewModel = trackingOnWithPin()
        viewModel.setTrackingEnabled(false)
        viewModel.currentState()
        viewModel.enterPin("0000")

        assertEquals(HomeStatus.Ready, viewModel.currentState().homeStatus)
        assertEquals(PinStep.EnterCurrent, viewModel.pinFlow.ui.value!!.step)
    }

    @Test
    fun cancellingThePinLeavesTheSettingAlone() {
        val viewModel = trackingOnWithPin()
        viewModel.setHistoryLength(HistoryLength.OneWeek)
        viewModel.currentState()
        viewModel.pinFlow.cancel()

        assertEquals(HistoryLength.FourWeeks, viewModel.currentState().historyLength)
        assertTrue(usage.deletedWith.isEmpty())
    }

    @Test
    fun afterThePinFurtherChangesDoNotAskAgainUntilTheAppIsLeft() {
        val viewModel = trackingOnWithPin()
        viewModel.setHistoryLength(HistoryLength.OneWeek)
        viewModel.currentState()
        viewModel.enterPin("4826")
        assertEquals(HistoryLength.OneWeek, viewModel.currentState().historyLength)

        viewModel.setHistoryLength(HistoryLength.OneYear)
        assertEquals(HistoryLength.OneYear, viewModel.currentState().historyLength)
        assertNull(viewModel.pinFlow.ui.value)

        // Leaving the app locks settings again.
        viewModel.onAppHidden()
        viewModel.setHistoryLength(HistoryLength.ThreeMonths)
        assertEquals(HistoryLength.OneYear, viewModel.currentState().historyLength)
        assertEquals(PinFlowKind.Verify, viewModel.pinFlow.ui.value!!.kind)
    }

    @Test
    fun turningTrackingOnNeverNeedsThePin() {
        val viewModel = trackingOnWithPin()
        viewModel.setTrackingEnabled(false)
        viewModel.currentState()
        viewModel.enterPin("4826")
        viewModel.onAppHidden()

        viewModel.setTrackingEnabled(true)
        assertEquals(HomeStatus.Ready, viewModel.currentState().homeStatus)
        assertNull(viewModel.pinFlow.ui.value)
    }

    @Test
    fun pinSettingOpensSetupOrChange() {
        val viewModel = newViewModel()
        viewModel.onPinSettingClick()
        viewModel.currentState()
        assertEquals(PinFlowKind.Setup, viewModel.pinFlow.ui.value!!.kind)
        assertEquals(false, viewModel.currentState().pinSet)

        viewModel.pinFlow.cancel()
        runBlocking { pinManager.setInitialPin(pin("4826")) }
        viewModel.onPinSettingClick()
        assertEquals(PinFlowKind.Change, viewModel.pinFlow.ui.value!!.kind)
        assertEquals(true, viewModel.currentState().pinSet)
    }

    @Test
    fun leavingTheAppClosesAnOpenPinScreen() {
        val viewModel = trackingOnWithPin()
        viewModel.setTrackingEnabled(false)
        viewModel.currentState()
        "48".forEach(viewModel.pinFlow::addDigit)

        viewModel.onAppHidden()
        assertNull(viewModel.pinFlow.ui.value)
        assertEquals(HomeStatus.Ready, viewModel.currentState().homeStatus)
    }

    // --- App limits ---

    private val hour = Duration.ofHours(1)

    @Test
    fun withoutAPinLimitsSaveStraightAway() {
        val viewModel = newViewModel()
        var done = false
        viewModel.saveLimit("video", AppLimit("video", hour)) { done = true }

        assertEquals(1, viewModel.currentState().limitCount)
        assertTrue(done)
        assertNull(viewModel.pinFlow.ui.value)
    }

    @Test
    fun withAPinSavingALimitAsksForItFirst() {
        runBlocking { pinManager.setInitialPin(pin("4826")) }
        val viewModel = newViewModel()
        var done = false
        viewModel.saveLimit("video", AppLimit("video", hour)) { done = true }
        viewModel.currentState()

        assertEquals(PinFlowKind.Verify, viewModel.pinFlow.ui.value!!.kind)
        assertEquals(0, viewModel.currentState().limitCount)
        assertFalse(done)

        viewModel.enterPin("4826")
        assertEquals(1, viewModel.currentState().limitCount)
        assertTrue(done)
    }

    @Test
    fun removingALimitAlsoNeedsThePin() {
        runBlocking {
            limits.save(AppLimit("video", hour))
            pinManager.setInitialPin(pin("4826"))
        }
        val viewModel = newViewModel()
        viewModel.saveLimit("video", null) {}
        viewModel.currentState()
        assertEquals(1, viewModel.currentState().limitCount)

        viewModel.enterPin("4826")
        assertEquals(0, viewModel.currentState().limitCount)
    }

    @Test
    fun dashboardMarksAppsWithLimits() {
        usage.usageByDay.value = mapOf(
            today to listOf(AppUsageTime("video", hour), AppUsageTime("mail", hour)),
        )
        runBlocking { limits.save(AppLimit("video", Duration.ofHours(2))) }
        val apps = newViewModel().currentState().dashboard!!.apps
        assertEquals(mapOf("mail" to false, "video" to true), apps.associate { it.packageName to it.hasLimit })
    }

    @Test
    fun appDetailShowsTheLimitAndTimeLeft() {
        usage.usageByDay.value = mapOf(today to listOf(AppUsageTime("video", Duration.ofMinutes(40))))
        runBlocking { limits.save(AppLimit("video", hour)) }
        val viewModel = newViewModel()
        var detail: org.openscreentime.ui.dashboard.AppDetail? = null
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate).launch {
            viewModel.appDetail("video").collect { detail = it }
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(hour, detail!!.limit!!.weekday)
        assertEquals(Duration.ofMinutes(20), detail.timeLeftToday)
        job.cancel()
    }

    @Test
    fun limitedAppsAreListedByNameAndLeftOutOfThePicker() {
        runBlocking {
            limits.save(AppLimit("video", hour))
            limits.save(AppLimit("chat", hour))
        }
        val viewModel = newViewModel()
        viewModel.currentState()
        assertEquals(listOf("Label of chat", "Label of video"), viewModel.limitedApps.value.map { it.label })

        val available = runBlocking { viewModel.appsWithoutLimit().first() }
        assertEquals(listOf("mail"), available.map { it.packageName })
    }

    // --- Downtime ---

    @Test
    fun downtimeStatusUsesTheSavedSchedule() {
        // The clock is Thursday 12:00 UTC; default downtime is 21:00–07:00.
        downtime.schedule.value = DowntimeSchedule(enabled = true)
        val status = newViewModel().let {
            it.currentState()
            it.downtime.value.status
        }
        assertEquals(DowntimeStatus.StartsAt(today.atTime(21, 0).atZone(zone)), status)
    }

    @Test
    fun withoutAPinDowntimeChangesSaveStraightAway() {
        val viewModel = newViewModel()
        viewModel.saveDowntime(DowntimeSchedule(enabled = true))
        viewModel.currentState()
        assertTrue(downtime.schedule.value.enabled)
    }

    @Test
    fun withAPinDowntimeChangesAskForIt() {
        runBlocking { pinManager.setInitialPin(pin("4826")) }
        val viewModel = newViewModel()
        viewModel.saveDowntime(DowntimeSchedule(enabled = true))
        viewModel.currentState()
        assertFalse(downtime.schedule.value.enabled)

        viewModel.enterPin("4826")
        assertTrue(downtime.schedule.value.enabled)
    }

    @Test
    fun daysAreInTheRegionsOrder() {
        assertEquals(java.time.DayOfWeek.SUNDAY, newViewModel().daysInOrder.first())
        assertEquals(7, newViewModel().daysInOrder.size)
    }
}
