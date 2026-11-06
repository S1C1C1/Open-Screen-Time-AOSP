package org.openscreentime.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.openscreentime.OpenScreenTimeApplication
import org.openscreentime.data.HistoryLength
import org.openscreentime.data.SettingsRepository
import org.openscreentime.usage.AppLabels
import org.openscreentime.usage.UsageAccessChecker
import org.openscreentime.usage.UsageRepository
import java.time.Clock
import java.time.LocalDate

/** Holds the app's state and handles what the user taps. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val settingsRepository: SettingsRepository,
    private val usageAccessChecker: UsageAccessChecker,
    private val usageRepository: UsageRepository,
    private val appLabels: AppLabels,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val hasUsageAccess = MutableStateFlow(usageAccessChecker.hasUsageAccess())
    private val usageAccessGuideOpen = MutableStateFlow(false)
    private val today = MutableStateFlow(LocalDate.now(clock))

    private val todayUsage = today
        .flatMapLatest { usageRepository.usageForDay(it) }
        .map { TodayUsage.from(it, appLabels) }

    val uiState: StateFlow<AppUiState> = combine(
        settingsRepository.settings,
        hasUsageAccess,
        usageAccessGuideOpen,
        todayUsage,
    ) { settings, hasAccess, guideOpen, usage ->
        AppUiState(settings, hasAccess, guideOpen, usage)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    /**
     * Called whenever the app comes to the front: re-checks usage access
     * (the user changes it in Settings, outside this app) and saves the
     * latest usage.
     */
    fun onAppVisible() {
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
