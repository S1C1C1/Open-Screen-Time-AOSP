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
import org.openscreentime.admin.EnforcementMode
import org.openscreentime.allowed.AllowedApp
import org.openscreentime.blocking.BlockDecision
import org.openscreentime.blocking.BlockReason
import org.openscreentime.blocking.LimitMode
import org.openscreentime.blocking.UnlockDuration
import org.openscreentime.allowed.AllowedRole
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
    private val allowed = FakeAlwaysAllowedRepository()
    private val overrides = FakeOverrideRepository()
    private val groups = FakeGroupRepository()
    private val schedules = FakeScheduleRepository()
    private val deviceOwner = FakeDeviceOwner()
    private val blocker = RecordingAppBlocker()
    private var enforcementChecks = 0
    private val restrictionsRepository = FakeRestrictionsRepository()
    private val applied = mutableListOf<Set<org.openscreentime.restrictions.Restriction>>()
    private val deviceClock = org.openscreentime.pin.FakeDeviceClock()
    private val protectionActions = org.openscreentime.admin.FakeProtectionActions()
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
        alwaysAllowedRepository = allowed,
        alwaysAllowedPolicy = testAlwaysAllowedPolicy(),
        overrideRepository = overrides,
        deviceOwner = deviceOwner,
        groupRepository = groups,
        scheduleRepository = schedules,
        appBlocker = blocker,
        requestEnforcementCheck = { enforcementChecks++ },
        restrictionsRepository = restrictionsRepository,
        restrictionApplier = { applied += listOf(it) },
        deviceClock = deviceClock,
        selfProtection = org.openscreentime.admin.SelfProtection(deviceOwner, protectionActions),
        clock = clock,
        firstDayOfWeek = DayOfWeek.SUNDAY,
        ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
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

    // --- Always allowed apps ---

    @Test
    fun alwaysAllowedStartsWithTheThreeDefaults() {
        val viewModel = newViewModel()
        assertEquals(3, viewModel.currentState().alwaysAllowedCount)
        assertEquals(
            listOf(AllowedRole.Phone, AllowedRole.Emergency, AllowedRole.Settings),
            viewModel.alwaysAllowed.value.map { it.app.role },
        )
    }

    @Test
    fun addingAndRemovingWithoutAPin() {
        val viewModel = newViewModel()
        var done = false
        viewModel.addAlwaysAllowed("mail") { done = true }
        assertTrue(done)
        assertEquals(4, viewModel.currentState().alwaysAllowedCount)

        viewModel.removeAlwaysAllowed(AllowedApp("mail", AllowedRole.Added))
        assertEquals(3, viewModel.currentState().alwaysAllowedCount)
    }

    @Test
    fun emergencyCallingStaysWhenRemovalIsTried() {
        val viewModel = newViewModel()
        viewModel.removeAlwaysAllowed(AllowedApp("emergency", AllowedRole.Emergency))
        assertEquals(3, viewModel.currentState().alwaysAllowedCount)
    }

    @Test
    fun withAPinChangesToAlwaysAllowedAskForIt() {
        runBlocking { pinManager.setInitialPin(pin("4826")) }
        val viewModel = newViewModel()
        viewModel.removeAlwaysAllowed(AllowedApp("settings", AllowedRole.Settings))
        viewModel.currentState()
        assertEquals(3, viewModel.currentState().alwaysAllowedCount)

        viewModel.enterPin("4826")
        assertEquals(2, viewModel.currentState().alwaysAllowedCount)
    }

    @Test
    fun pickerLeavesOutAppsAlreadyAllowed() {
        installed = listOf(InstalledApp("settings", "Settings"), InstalledApp("mail", "Mail"))
        val viewModel = newViewModel()
        viewModel.currentState()
        val available = runBlocking { viewModel.appsNotAlwaysAllowed().first() }
        assertEquals(listOf("mail"), available.map { it.packageName })
    }

    @Test
    fun limitEditorKnowsWhenAnAppIsAlwaysAllowed() {
        val viewModel = newViewModel()
        assertTrue(runBlocking { viewModel.isAlwaysAllowed("dialer").first() })
        assertFalse(runBlocking { viewModel.isAlwaysAllowed("video").first() })
    }

    // --- Block screens (the clock is Thursday 12:00 UTC) ---

    private val noon = java.time.Instant.parse("2026-11-05T12:00:00Z")

    private fun MainViewModel.preview(reason: BlockReason = BlockReason.LimitReached, pkg: String? = "video") {
        previewBlockScreen(pkg, reason)
        currentState()
    }

    @Test
    fun previewUsesTheCurrentModeAndApp() {
        val viewModel = newViewModel()
        viewModel.preview()
        val ui = viewModel.blockScreenUi.value!!
        assertEquals("Label of video", ui.label)
        assertEquals(LimitMode.Strict, ui.mode)
        assertEquals(today.plusDays(1).atStartOfDay(zone), ui.blockedUntil)
    }

    @Test
    fun graceGivesOneMoreMinuteOncePerDay() {
        settings.state(LimitMode.Grace)
        val viewModel = newViewModel()
        viewModel.preview()
        assertTrue(viewModel.blockScreenUi.value!!.graceAvailable)

        viewModel.blockOneMoreMinute()
        viewModel.currentState()
        assertNull(viewModel.blockScreenUi.value)
        assertEquals(noon.plusSeconds(60), overrides.saved.value["video"]!!.allowedUntil)
        assertEquals(today, overrides.saved.value["video"]!!.graceUsedOn)

        viewModel.preview()
        assertFalse(viewModel.blockScreenUi.value!!.graceAvailable)
    }

    @Test
    fun reminderCanBeIgnoredForFifteenMinutes() {
        settings.state(LimitMode.Reminder)
        val viewModel = newViewModel()
        viewModel.preview()
        viewModel.blockIgnore()
        viewModel.currentState()
        assertEquals(noon.plusSeconds(15 * 60), overrides.saved.value["video"]!!.allowedUntil)
    }

    @Test
    fun withAPinMoreTimeNeedsThePinThenAChoice() {
        runBlocking { pinManager.setInitialPin(pin("4826")) }
        val viewModel = newViewModel()
        viewModel.preview()
        viewModel.blockEnterPin()
        viewModel.currentState()
        assertFalse(viewModel.blockScreenUi.value!!.choosingUnlock)

        viewModel.enterPin("4826")
        assertTrue(viewModel.blockScreenUi.value!!.choosingUnlock)

        viewModel.blockUnlock(UnlockDuration.OneHour)
        viewModel.currentState()
        assertEquals(noon.plusSeconds(3600), overrides.saved.value["video"]!!.allowedUntil)
        assertNull(viewModel.blockScreenUi.value)
    }

    @Test
    fun wrongPinGivesNoMoreTime() {
        runBlocking { pinManager.setInitialPin(pin("4826")) }
        val viewModel = newViewModel()
        viewModel.preview()
        viewModel.blockEnterPin()
        viewModel.currentState()
        viewModel.enterPin("0000")
        assertFalse(viewModel.blockScreenUi.value!!.choosingUnlock)
        assertTrue(overrides.saved.value.isEmpty())
    }

    @Test
    fun withoutAPinTheChoiceShowsStraightAway() {
        val viewModel = newViewModel()
        viewModel.preview()
        viewModel.blockEnterPin()
        viewModel.currentState()
        assertTrue(viewModel.blockScreenUi.value!!.choosingUnlock)
    }

    @Test
    fun restOfDayMeansMidnightForALimitAndTheEndForDowntime() {
        downtime.schedule.value = DowntimeSchedule(enabled = true)
        val viewModel = newViewModel()

        viewModel.preview(BlockReason.LimitReached)
        viewModel.blockEnterPin()
        viewModel.currentState()
        viewModel.blockUnlock(UnlockDuration.RestOfDay)
        viewModel.currentState()
        assertEquals(today.plusDays(1).atStartOfDay(zone).toInstant(), overrides.saved.value["video"]!!.allowedUntil)

        viewModel.preview(BlockReason.Downtime, pkg = "maps")
        assertEquals(today.plusDays(1).atTime(7, 0).atZone(zone), viewModel.blockScreenUi.value!!.blockedUntil)
        viewModel.blockEnterPin()
        viewModel.currentState()
        viewModel.blockUnlock(UnlockDuration.RestOfDay)
        viewModel.currentState()
        assertEquals(today.plusDays(1).atTime(7, 0).atZone(zone).toInstant(), overrides.saved.value["maps"]!!.allowedUntil)
    }

    @Test
    fun previewWithoutAnAppSavesNothing() {
        val viewModel = newViewModel()
        viewModel.preview(BlockReason.Downtime, pkg = null)
        assertNull(viewModel.blockScreenUi.value!!.label)
        viewModel.blockIgnore()
        viewModel.currentState()
        assertTrue(overrides.saved.value.isEmpty())
    }

    @Test
    fun leavingTheAppClosesTheBlockScreen() {
        val viewModel = newViewModel()
        viewModel.preview()
        viewModel.onAppHidden()
        assertNull(viewModel.blockScreenUi.value)
    }

    @Test
    fun blockDecisionReflectsUsageLimitAndExtraTime() {
        usage.usageByDay.value = mapOf(today to listOf(AppUsageTime("video", Duration.ofMinutes(70))))
        runBlocking { limits.save(AppLimit("video", Duration.ofHours(1))) }
        val viewModel = newViewModel()

        fun decision(): BlockDecision {
            var result: BlockDecision? = null
            val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate).launch {
                viewModel.blockDecision("video").collect { result = it }
            }
            shadowOf(Looper.getMainLooper()).idle()
            job.cancel()
            return result!!
        }

        assertTrue(decision() is BlockDecision.Blocked)
        runBlocking { overrides.allowUntil("video", noon.plusSeconds(600)) }
        assertEquals(BlockDecision.AllowedUntil(noon.plusSeconds(600)), decision())
    }

    @Test
    fun changingTheModeNeedsThePinOnceSet() {
        runBlocking { pinManager.setInitialPin(pin("4826")) }
        val viewModel = newViewModel()
        viewModel.setLimitMode(LimitMode.Reminder)
        viewModel.currentState()
        assertEquals(LimitMode.Strict, viewModel.currentState().limitMode)
        viewModel.enterPin("4826")
        assertEquals(LimitMode.Reminder, viewModel.currentState().limitMode)
    }

    private fun FakeSettingsRepository.state(mode: LimitMode) = runBlocking { setLimitMode(mode) }

    // --- Device owner ---

    @Test
    fun enforcementModeIsRefreshedWhenTheAppIsOpened() {
        val viewModel = newViewModel()
        assertEquals(EnforcementMode.PreviewOnly, viewModel.enforcementMode.value)

        // Set up from a computer while the app was in the background.
        deviceOwner.current = EnforcementMode.DeviceOwner
        viewModel.onAppVisible()
        assertEquals(EnforcementMode.DeviceOwner, viewModel.enforcementMode.value)
    }

    @Test
    fun removingDeviceOwnerNeedsThePinOnceSet() {
        deviceOwner.current = EnforcementMode.DeviceOwner
        runBlocking { pinManager.setInitialPin(pin("4826")) }
        val viewModel = newViewModel()

        viewModel.removeDeviceOwner()
        viewModel.currentState()
        assertEquals(EnforcementMode.DeviceOwner, viewModel.enforcementMode.value)

        viewModel.enterPin("4826")
        assertEquals(EnforcementMode.PreviewOnly, viewModel.enforcementMode.value)
    }

    // --- Real blocking (device-owner mode) ---

    @Test
    fun realBlockScreenHasNoPreviewBanner() {
        val viewModel = newViewModel()
        viewModel.openBlockScreen("video", BlockReason.LimitReached, real = true)
        viewModel.currentState()
        assertFalse(viewModel.blockScreenUi.value!!.isPreview)
    }

    @Test
    fun extraTimeAsksForAnImmediateRecheck() {
        settings.state(LimitMode.Reminder)
        val viewModel = newViewModel()
        viewModel.openBlockScreen("video", BlockReason.LimitReached, real = true)
        viewModel.currentState()
        viewModel.blockIgnore()
        viewModel.currentState()
        assertEquals(1, enforcementChecks)
    }

    @Test
    fun removingDeviceOwnerUnpausesEverythingFirst() {
        deviceOwner.current = EnforcementMode.DeviceOwner
        val viewModel = newViewModel()
        viewModel.removeDeviceOwner()
        viewModel.currentState()
        assertEquals(listOf(emptySet<String>()), blocker.calls)
        assertEquals(EnforcementMode.PreviewOnly, viewModel.enforcementMode.value)
    }

    // --- Self-protection ---

    @Test
    fun selfProtectionTurnsOnWhenAPinIsSet() {
        deviceOwner.current = EnforcementMode.DeviceOwner
        val viewModel = newViewModel()
        viewModel.currentState()
        assertTrue(protectionActions.allOff)

        runBlocking { pinManager.setInitialPin(pin("4826")) }
        viewModel.currentState()
        assertTrue(protectionActions.allOn)
    }

    @Test
    fun removingDeviceOwnerUndoesSelfProtection() {
        deviceOwner.current = EnforcementMode.DeviceOwner
        runBlocking { pinManager.setInitialPin(pin("4826")) }
        val viewModel = newViewModel()
        viewModel.currentState()
        assertTrue(protectionActions.allOn)

        viewModel.removeDeviceOwner()
        viewModel.currentState()
        viewModel.enterPin("4826")
        assertTrue(protectionActions.allOff)
    }

    // --- Restrictions ---

    private val installs = org.openscreentime.restrictions.Restriction.Installs

    @Test
    fun turningARestrictionOnNeverNeedsThePin() {
        runBlocking { pinManager.setInitialPin(pin("4826")) }
        val viewModel = newViewModel()
        viewModel.setRestriction(installs, true)
        viewModel.currentState()
        assertEquals(setOf(installs), viewModel.restrictions.value.enabled)
        assertEquals(setOf(installs), applied.last())
        assertNull(viewModel.pinFlow.ui.value)
    }

    @Test
    fun turningARestrictionOffNeedsThePin() {
        runBlocking {
            pinManager.setInitialPin(pin("4826"))
            restrictionsRepository.setEnabled(installs, true)
        }
        val viewModel = newViewModel()
        viewModel.setRestriction(installs, false)
        viewModel.currentState()
        assertEquals(setOf(installs), viewModel.restrictions.value.enabled)

        viewModel.enterPin("4826")
        assertEquals(emptySet<Any>(), viewModel.restrictions.value.enabled)
        assertEquals(emptySet<Any>(), applied.last())
    }

    @Test
    fun allowingForFifteenMinutesLiftsItAndAsksForARecheck() {
        runBlocking {
            pinManager.setInitialPin(pin("4826"))
            restrictionsRepository.setEnabled(installs, true)
        }
        val viewModel = newViewModel()
        viewModel.allowRestrictionFor15Minutes(installs)
        viewModel.currentState()
        viewModel.enterPin("4826")

        assertEquals(emptySet<Any>(), applied.last())
        assertEquals(1, enforcementChecks)
        assertEquals(noon.plusSeconds(15 * 60), viewModel.restrictions.value.liftedUntil[installs])
        // Still switched on: it comes back by itself.
        assertEquals(setOf(installs), viewModel.restrictions.value.enabled)
    }

    @Test
    fun removingDeviceOwnerClearsRestrictionsFirst() {
        deviceOwner.current = EnforcementMode.DeviceOwner
        val viewModel = newViewModel()
        viewModel.removeDeviceOwner()
        viewModel.currentState()
        assertEquals(emptySet<Any>(), applied.last())
    }
    // --- App groups ---

    @Test
    fun newGroupIsSavedWithoutAPin() {
        val viewModel = newViewModel()
        viewModel.startNewGroup()
        viewModel.updateGroupDraft { it.copy(name = " Social ").toggle("chat").toggle("video") }
        viewModel.updateGroupDraft { it.copy(limitOn = true, weekdayMinutes = 45) }
        var done = false
        viewModel.saveGroup { done = true }

        assertTrue(done)
        assertNull(viewModel.groupDraft.value)
        val saved = viewModel.groups.value.single()
        assertEquals("Social", saved.name)
        assertEquals(setOf("chat", "video"), saved.packages)
        assertEquals(org.openscreentime.groups.DailyLimit(Duration.ofMinutes(45)), saved.limit)
        assertEquals(1, viewModel.currentState().groupCount)
        assertEquals(1, viewModel.currentState().groupLimitCount)
        assertEquals(1, enforcementChecks)
    }

    @Test
    fun aGroupWithoutANameCantBeSaved() {
        val viewModel = newViewModel()
        viewModel.startNewGroup(name = "   ")
        viewModel.saveGroup {}
        assertTrue(viewModel.groups.value.isEmpty())
        assertFalse(viewModel.groupDraft.value!!.canSave)
    }

    @Test
    fun withAPinChangingOrDeletingAGroupAsksForIt() {
        runBlocking {
            groups.save(org.openscreentime.groups.AppGroup(name = "Games", packages = setOf("video")))
            pinManager.setInitialPin(pin("4826"))
        }
        val viewModel = newViewModel()
        viewModel.currentState()
        val id = viewModel.groups.value.single().id

        viewModel.editGroup(id)
        viewModel.updateGroupDraft { it.toggle("chat") }
        viewModel.saveGroup {}
        viewModel.currentState()
        assertEquals(PinFlowKind.Verify, viewModel.pinFlow.ui.value!!.kind)
        assertEquals(setOf("video"), viewModel.groups.value.single().packages)

        viewModel.enterPin("4826")
        assertEquals(setOf("video", "chat"), viewModel.groups.value.single().packages)

        // Still unlocked until the app is left, so deleting doesn't ask again.
        viewModel.editGroup(id)
        viewModel.deleteGroup {}
        viewModel.currentState()
        assertTrue(viewModel.groups.value.isEmpty())
    }

    @Test
    fun suggestionsComeFromAppCategories() {
        installed = listOf(
            InstalledApp("chat", "Chat", android.content.pm.ApplicationInfo.CATEGORY_SOCIAL),
            InstalledApp("photos", "Photos", android.content.pm.ApplicationInfo.CATEGORY_SOCIAL),
        )
        val viewModel = newViewModel()
        val suggestions = runBlocking { viewModel.groupSuggestions().first() }
        assertEquals(listOf(setOf("chat", "photos")), suggestions.map { it.packages })
    }

    @Test
    fun blockDecisionIncludesTheGroupsSharedTotal() {
        usage.usageByDay.value = mapOf(
            today to listOf(AppUsageTime("video", Duration.ofMinutes(40)), AppUsageTime("chat", Duration.ofMinutes(20))),
        )
        runBlocking {
            groups.save(
                org.openscreentime.groups.AppGroup(
                    name = "Fun",
                    packages = setOf("video", "chat"),
                    limit = org.openscreentime.groups.DailyLimit(Duration.ofHours(1)),
                ),
            )
        }
        val viewModel = newViewModel()
        var result: BlockDecision? = null
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate).launch {
            viewModel.blockDecision("chat").collect { result = it }
        }
        shadowOf(Looper.getMainLooper()).idle()
        job.cancel()
        assertEquals("Fun", (result as BlockDecision.Blocked).ruleName)

        // Apps in a limited group get the limit badge on the dashboard.
        accessGranted = true
        assertTrue(newViewModel().currentState().dashboard!!.apps.first { it.packageName == "chat" }.hasLimit)
    }
    // --- Schedules ---

    @Test
    fun presetFillsTheDraftAndSavesWithoutAPin() {
        val viewModel = newViewModel()
        viewModel.startNewSchedule(org.openscreentime.schedules.SchedulePreset.Bedtime, "Bedtime")
        val draft = viewModel.scheduleDraft.value!!
        assertEquals(java.time.LocalTime.of(21, 0), draft.start)
        assertEquals(java.time.LocalTime.of(6, 0), draft.end)
        assertEquals(DayOfWeek.entries.toSet(), draft.days)

        viewModel.updateScheduleDraft { it.toggleApp("video").toggleGroup(4) }
        var done = false
        viewModel.saveSchedule { done = true }

        assertTrue(done)
        val saved = viewModel.schedules.value.single()
        assertEquals("Bedtime", saved.name)
        assertEquals(setOf("video"), saved.packages)
        assertEquals(setOf(4L), saved.groupIds)
        assertEquals(1, viewModel.currentState().scheduleOnCount)
        assertEquals(1, enforcementChecks)
    }

    @Test
    fun aScheduleNeedsANameAndADay() {
        val viewModel = newViewModel()
        viewModel.startNewSchedule(name = "Homework")
        viewModel.updateScheduleDraft { draft -> draft.days.fold(draft) { d, day -> d.toggleDay(day) } }
        assertFalse(viewModel.scheduleDraft.value!!.canSave)
        viewModel.saveSchedule {}
        assertTrue(viewModel.schedules.value.isEmpty())
    }

    @Test
    fun withAPinChangingOrDeletingAScheduleAsksForIt() {
        runBlocking {
            schedules.save(
                org.openscreentime.schedules.AppSchedule(
                    name = "Work",
                    days = org.openscreentime.schedules.SchedulePreset.WorkSchool.days,
                    window = org.openscreentime.schedules.SchedulePreset.WorkSchool.window,
                ),
            )
            pinManager.setInitialPin(pin("4826"))
        }
        val viewModel = newViewModel()
        viewModel.currentState()
        val id = viewModel.schedules.value.single().id

        viewModel.editSchedule(id)
        viewModel.updateScheduleDraft { it.copy(enabled = false) }
        viewModel.saveSchedule {}
        viewModel.currentState()
        assertEquals(PinFlowKind.Verify, viewModel.pinFlow.ui.value!!.kind)
        assertTrue(viewModel.schedules.value.single().enabled)

        viewModel.enterPin("4826")
        assertFalse(viewModel.schedules.value.single().enabled)

        viewModel.editSchedule(id)
        viewModel.deleteSchedule {}
        viewModel.currentState()
        assertTrue(viewModel.schedules.value.isEmpty())
    }

    @Test
    fun aScheduleBlocksItsGroupsAppsAndTheBlockScreenNamesIt() {
        // The test clock is Thursday 12:00, during work hours.
        runBlocking {
            val groupId = groups.save(org.openscreentime.groups.AppGroup(name = "Social", packages = setOf("chat")))
            schedules.save(
                org.openscreentime.schedules.AppSchedule(
                    name = "Work",
                    days = org.openscreentime.schedules.SchedulePreset.WorkSchool.days,
                    window = org.openscreentime.schedules.SchedulePreset.WorkSchool.window,
                    groupIds = setOf(groupId),
                ),
            )
        }
        val viewModel = newViewModel()
        viewModel.currentState()
        var result: BlockDecision? = null
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate).launch {
            viewModel.blockDecision("chat").collect { result = it }
        }
        shadowOf(Looper.getMainLooper()).idle()
        job.cancel()
        val blocked = result as BlockDecision.Blocked
        assertEquals(org.openscreentime.blocking.BlockReason.Scheduled, blocked.reason)
        assertEquals("Work", blocked.ruleName)
        assertEquals(today.atTime(17, 0).atZone(zone), blocked.blockedUntil)

        viewModel.openBlockScreen("chat", blocked.reason, real = true, blocked.ruleName)
        viewModel.currentState()
        val ui = viewModel.blockScreenUi.value!!
        assertEquals("Work", ui.ruleName)
        assertEquals(today.atTime(17, 0).atZone(zone), ui.blockedUntil)
    }
    // --- Groups and schedules shown on screens ---

    @Test
    fun activeSchedulesAndDashboardBadgeFollowTheClock() {
        // The test clock is Thursday 12:00: Work is on, Bedtime isn't.
        accessGranted = true
        usage.usageByDay.value = mapOf(today to listOf(AppUsageTime("chat", Duration.ofMinutes(5)), AppUsageTime("mail", Duration.ofMinutes(5))))
        runBlocking {
            schedules.save(
                org.openscreentime.schedules.AppSchedule(
                    name = "Work",
                    days = org.openscreentime.schedules.SchedulePreset.WorkSchool.days,
                    window = org.openscreentime.schedules.SchedulePreset.WorkSchool.window,
                    packages = setOf("chat"),
                ),
            )
            schedules.save(
                org.openscreentime.schedules.AppSchedule(
                    name = "Bedtime",
                    days = org.openscreentime.schedules.SchedulePreset.Bedtime.days,
                    window = org.openscreentime.schedules.SchedulePreset.Bedtime.window,
                    packages = setOf("mail"),
                ),
            )
        }
        val viewModel = newViewModel()
        val apps = viewModel.currentState().dashboard!!.apps
        assertTrue(apps.first { it.packageName == "chat" }.scheduledNow)
        assertFalse(apps.first { it.packageName == "mail" }.scheduledNow)
        assertEquals(listOf("Work"), viewModel.activeSchedules.value.map { it.name })
    }
}

/** Records what blocking was asked for. */
class RecordingAppBlocker : org.openscreentime.blocking.AppBlocker {
    val calls = mutableListOf<Set<String>>()
    override suspend fun setBlockedApps(packages: Set<String>) {
        calls += packages
    }
}
