package org.openscreentime.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.openscreentime.OpenScreenTimeApplication
import org.openscreentime.data.HistoryLength
import org.openscreentime.data.SettingsRepository
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
    private val clock: Clock = Clock.systemDefaultZone(),
    /** First day of the week, from the phone's region (Sunday in the US, Monday in most of Europe). */
    private val firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek,
) : ViewModel() {

    private val hasUsageAccess = MutableStateFlow(usageAccessChecker.hasUsageAccess())
    private val usageAccessGuideOpen = MutableStateFlow(false)
    private val today = MutableStateFlow(LocalDate.now(clock))

    /** The day picked in the week chart; null means today. */
    private val selectedDay = MutableStateFlow<LocalDate?>(null)

    /** Saved usage for every day of the current week. */
    private val weekUsage = today.flatMapLatest { day ->
        val week = weekContaining(day, firstDayOfWeek)
        usageRepository.usageForDays(week.first(), week.last())
    }

    private val dashboard = combine(weekUsage, today, selectedDay) { usage, day, selected ->
        buildDashboard(usage, day, selected, firstDayOfWeek, appLabels)
    }

    val uiState: StateFlow<AppUiState> = combine(
        settingsRepository.settings,
        hasUsageAccess,
        usageAccessGuideOpen,
        dashboard,
    ) { settings, hasAccess, guideOpen, dashboard ->
        AppUiState(settings, hasAccess, guideOpen, dashboard)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    /**
     * Called whenever the app comes to the front: re-checks usage access
     * (the user changes it in Settings, outside this app) and saves the
     * latest usage.
     */
    fun onAppVisible() {
        // Coming back to the app starts again from today.
        selectedDay.value = null
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

    // TODO(PIN step): turning tracking off must require the PIN once one is set.
    fun setTrackingEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setTrackingEnabled(enabled) }
        if (enabled) {
            openGuideIfNeeded()
            syncUsage()
        }
    }

    // TODO(PIN step): changing settings must require the PIN once one is set.
    fun setHistoryLength(length: HistoryLength) {
        viewModelScope.launch {
            settingsRepository.setHistoryLength(length)
            // Apply a shorter setting right away rather than at the next background run.
            usageRepository.deleteOldHistory(length)
        }
    }

    /** Shows [day] in the dashboard. Days after today are ignored. */
    fun selectDay(day: LocalDate) {
        if (!day.isAfter(today.value)) selectedDay.value = day
    }

    /** One app's usage this week, for its detail screen. */
    fun appDetail(packageName: String): Flow<AppDetail> =
        combine(weekUsage, today) { usage, day ->
            buildAppDetail(packageName, usage, day, firstDayOfWeek, appLabels)
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
                )
            }
        }
    }
}
