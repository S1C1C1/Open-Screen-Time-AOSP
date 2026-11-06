package org.openscreentime.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscreentime.OpenScreenTimeApplication
import org.openscreentime.data.HistoryLength
import org.openscreentime.data.SettingsRepository
import org.openscreentime.downtime.DowntimeRepository
import org.openscreentime.downtime.DowntimeSchedule
import org.openscreentime.downtime.DowntimeStatus
import org.openscreentime.limits.AppLimit
import org.openscreentime.limits.LimitRepository
import org.openscreentime.pin.PinManager
import org.openscreentime.ui.limits.LimitedApp
import org.openscreentime.usage.InstalledApp
import org.openscreentime.usage.InstalledApps
import org.openscreentime.ui.pin.PinFlowController
import org.openscreentime.ui.pin.PinFlowKind
import org.openscreentime.ui.dashboard.AppDetail
import org.openscreentime.ui.dashboard.buildAppDetail
import org.openscreentime.ui.dashboard.buildDashboard
import org.openscreentime.ui.dashboard.weekContaining
import org.openscreentime.usage.AppLabels
import org.openscreentime.usage.UsageAccessChecker
import org.openscreentime.usage.UsageRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

/** Holds the app's state and handles what the user taps. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val settingsRepository: SettingsRepository,
    private val usageAccessChecker: UsageAccessChecker,
    private val usageRepository: UsageRepository,
    private val appLabels: AppLabels,
    private val pinManager: PinManager,
    private val limitRepository: LimitRepository,
    private val installedApps: InstalledApps,
    private val downtimeRepository: DowntimeRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    /** First day of the week, from the phone's region (Sunday in the US, Monday in most of Europe). */
    private val firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek,
) : ViewModel() {

    /** The PIN screens. */
    val pinFlow = PinFlowController(pinManager)

    /**
     * True after the PIN has been entered, until the app is left. While true,
     * protected settings can be changed without asking again.
     */
    private val settingsUnlocked = MutableStateFlow(false)

    private val hasUsageAccess = MutableStateFlow(usageAccessChecker.hasUsageAccess())
    private val usageAccessGuideOpen = MutableStateFlow(false)
    private val today = MutableStateFlow(LocalDate.now(clock))

    /** The moment the downtime status is worked out for; refreshed when the app is opened. */
    private val now = MutableStateFlow(clock.instant())

    /** Days of the week in the order the phone's region uses. */
    val daysInOrder: List<DayOfWeek> = (0L until 7L).map { firstDayOfWeek.plus(it) }

    /** The downtime schedule and whether it's on right now. */
    val downtime: StateFlow<DowntimeUi> = combine(downtimeRepository.schedule, now) { schedule, instant ->
        DowntimeUi(schedule, schedule.status(instant, clock.zone), instant.atZone(clock.zone).toLocalDate())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DowntimeUi())

    /** The day picked in the week chart; null means today. */
    private val selectedDay = MutableStateFlow<LocalDate?>(null)

    /** Saved usage for every day of the current week. */
    private val weekUsage = today.flatMapLatest { day ->
        val week = weekContaining(day, firstDayOfWeek)
        usageRepository.usageForDays(week.first(), week.last())
    }

    private val dashboard = combine(weekUsage, today, selectedDay, limitRepository.limits) { usage, day, selected, limits ->
        buildDashboard(usage, day, selected, firstDayOfWeek, appLabels, limits.map { it.packageName }.toSet())
    }

    /** Apps with limits, with their names, sorted by name. */
    val limitedApps: StateFlow<List<LimitedApp>> = limitRepository.limits
        .map { limits ->
            limits.map { LimitedApp(it, appLabels.labelFor(it.packageName)) }.sortedBy { it.label.lowercase() }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val uiState: StateFlow<AppUiState> = combine(
        combine(settingsRepository.settings, hasUsageAccess, usageAccessGuideOpen, ::Triple),
        dashboard,
        pinManager.isPinSet,
        limitedApps,
    ) { (settings, hasAccess, guideOpen), dashboard, pinSet, limited ->
        AppUiState(settings, hasAccess, guideOpen, dashboard, pinSet, limitCount = limited.size)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    /**
     * Called whenever the app comes to the front: re-checks usage access
     * (the user changes it in Settings, outside this app) and saves the
     * latest usage.
     */
    fun onAppVisible() {
        // Coming back to the app starts again from today.
        selectedDay.value = null
        now.value = clock.instant()
        refreshUsageAccess()
        syncUsage()
    }

    fun refreshUsageAccess() {
        val granted = usageAccessChecker.hasUsageAccess()
        hasUsageAccess.value = granted
        if (granted) usageAccessGuideOpen.value = false
    }

    fun onWelcomeTurnOn() {
        viewModelScope.launch { settingsRepository.completeOnboarding(trackingEnabled = true) }
        openGuideIfNeeded()
        syncUsage()
    }

    fun onWelcomeNotNow() {
        viewModelScope.launch { settingsRepository.completeOnboarding(trackingEnabled = false) }
    }

    fun setTrackingEnabled(enabled: Boolean) {
        if (enabled) {
            // Turning screen time on only adds protection, so it never needs the PIN.
            viewModelScope.launch { settingsRepository.setTrackingEnabled(true) }
            openGuideIfNeeded()
            syncUsage()
        } else {
            withPin { settingsRepository.setTrackingEnabled(false) }
        }
    }

    fun setHistoryLength(length: HistoryLength) = withPin {
        settingsRepository.setHistoryLength(length)
        // Apply a shorter setting right away rather than at the next background run.
        usageRepository.deleteOldHistory(length)
    }

    /** Opens PIN setup, or PIN change if a PIN exists (which asks for the current PIN). */
    fun onPinSettingClick() {
        viewModelScope.launch {
            val kind = if (pinManager.isPinSet.first()) PinFlowKind.Change else PinFlowKind.Setup
            pinFlow.start(kind)
        }
    }

    fun submitPin() {
        viewModelScope.launch { pinFlow.submit() }
    }

    /**
     * Called when the app goes to the background: settings lock again, and
     * any PIN screen closes and forgets what was typed.
     */
    fun onAppHidden() {
        settingsUnlocked.value = false
        pinFlow.cancel()
    }

    /**
     * Runs [change] now if no PIN is set or settings are unlocked; otherwise
     * asks for the PIN first and runs it only after the correct PIN.
     */
    private fun withPin(change: suspend () -> Unit) {
        viewModelScope.launch {
            if (settingsUnlocked.value || !pinManager.isPinSet.first()) {
                change()
            } else {
                pinFlow.start(PinFlowKind.Verify) {
                    settingsUnlocked.value = true
                    viewModelScope.launch { change() }
                }
            }
        }
    }

    /** Shows [day] in the dashboard. Days after today are ignored. */
    fun selectDay(day: LocalDate) {
        if (!day.isAfter(today.value)) selectedDay.value = day
    }

    /** One app's usage this week, for its detail screen. */
    fun appDetail(packageName: String): Flow<AppDetail> =
        combine(weekUsage, today, limitRepository.limitFor(packageName)) { usage, day, limit ->
            buildAppDetail(packageName, usage, day, firstDayOfWeek, appLabels, limit)
        }

    /** One app's saved limit, for the limit editor. */
    fun limitFor(packageName: String): Flow<AppLimit?> = limitRepository.limitFor(packageName)

    fun labelFor(packageName: String): String = appLabels.labelFor(packageName)

    /** Apps a new limit can be added for: launchable apps that don't have one yet. */
    fun appsWithoutLimit(): Flow<List<InstalledApp>> = limitRepository.limits.map { limits ->
        val limited = limits.map { it.packageName }.toSet()
        withContext(Dispatchers.IO) { installedApps.launchableApps() }.filter { it.packageName !in limited }
    }

    /**
     * Saves [limit] for [packageName], or removes the limit if it's null.
     * Asks for the PIN first when one is set. [onDone] runs after saving.
     */
    fun saveLimit(packageName: String, limit: AppLimit?, onDone: () -> Unit) = withPin {
        if (limit == null) limitRepository.remove(packageName) else limitRepository.save(limit)
        onDone()
    }

    /** Saves a changed downtime schedule, asking for the PIN first when one is set. */
    fun saveDowntime(schedule: DowntimeSchedule) = withPin {
        downtimeRepository.save(schedule)
        now.value = clock.instant()
    }

    /** Opens PIN setup; used by the reminders on the App limits and Downtime screens. */
    fun startPinSetup() {
        viewModelScope.launch { pinFlow.start(PinFlowKind.Setup) }
    }

    fun openUsageAccessGuide() {
        usageAccessGuideOpen.value = true
    }

    fun closeUsageAccessGuide() {
        usageAccessGuideOpen.value = false
    }

    private fun openGuideIfNeeded() {
        if (!hasUsageAccess.value) usageAccessGuideOpen.value = true
    }

    /** Saves the latest usage, if tracking is on and allowed. */
    private fun syncUsage() {
        // The date may have changed since the app was last open.
        today.value = LocalDate.now(clock)
        viewModelScope.launch {
            if (!hasUsageAccess.value) return@launch
            // Read settings after any pending save above has finished.
            if (!settingsRepository.settings.first().trackingEnabled) return@launch
            try {
                usageRepository.sync()
            } catch (e: Exception) {
                // Not fatal: the background job will try again. Never log usage data.
                Log.w(TAG, "Usage sync failed: ${e.javaClass.simpleName}")
            }
        }
    }

    companion object {
        private const val TAG = "MainViewModel"

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (context.applicationContext as OpenScreenTimeApplication).container
                MainViewModel(
                    container.settingsRepository,
                    container.usageAccessChecker,
                    container.usageRepository,
                    container.appLabels,
                    container.pinManager,
                    container.limitRepository,
                    container.installedApps,
                    container.downtimeRepository,
                )
            }
        }
    }
}

/** What the Downtime screen and its Settings row show. */
data class DowntimeUi(
    val schedule: DowntimeSchedule = DowntimeSchedule(),
    val status: DowntimeStatus = DowntimeStatus.Off,
    val today: LocalDate = LocalDate.MIN,
)
