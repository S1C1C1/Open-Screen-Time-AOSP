package org.openscreentime.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.openscreentime.data.DataStoreSettingsRepository
import org.openscreentime.data.SettingsRepository
import org.openscreentime.data.settingsDataStore
import org.openscreentime.usage.AppOpsUsageAccessChecker
import org.openscreentime.usage.UsageAccessChecker

/** Holds the app's state and handles what the user taps. */
class MainViewModel(
    private val settingsRepository: SettingsRepository,
    private val usageAccessChecker: UsageAccessChecker,
) : ViewModel() {

    private val hasUsageAccess = MutableStateFlow(usageAccessChecker.hasUsageAccess())
    private val usageAccessGuideOpen = MutableStateFlow(false)

    val uiState: StateFlow<AppUiState> = combine(
        settingsRepository.settings,
        hasUsageAccess,
        usageAccessGuideOpen,
    ) { settings, hasAccess, guideOpen ->
        AppUiState(settings, hasAccess, guideOpen)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    /**
     * Re-checks usage access. Called whenever the app comes back to the
     * front, because the user changes it in Settings, outside this app.
     */
    fun refreshUsageAccess() {
        val granted = usageAccessChecker.hasUsageAccess()
        hasUsageAccess.value = granted
        if (granted) usageAccessGuideOpen.value = false
    }

    fun onWelcomeTurnOn() {
        viewModelScope.launch { settingsRepository.completeOnboarding(trackingEnabled = true) }
        openGuideIfNeeded()
    }

    fun onWelcomeNotNow() {
        viewModelScope.launch { settingsRepository.completeOnboarding(trackingEnabled = false) }
    }

    // TODO(PIN step): turning tracking off must require the PIN once one is set.
    fun setTrackingEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setTrackingEnabled(enabled) }
        if (enabled) openGuideIfNeeded()
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

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                MainViewModel(
                    DataStoreSettingsRepository(appContext.settingsDataStore),
                    AppOpsUsageAccessChecker(appContext),
                )
            }
        }
    }
}
