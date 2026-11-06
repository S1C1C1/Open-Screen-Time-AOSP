package org.openscreentime.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openscreentime.ui.home.HomeScreen
import org.openscreentime.ui.usageaccess.UsageAccessScreen
import org.openscreentime.ui.welcome.WelcomeScreen

/**
 * Shows the right screen for the current state. With only a few screens,
 * this is simpler than a navigation library.
 */
@Composable
fun OpenScreenTimeApp(
    viewModel: MainViewModel,
    onOpenUsageAccessSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (state.screen) {
        // Settings load in a moment; show a plain background meanwhile.
        AppScreen.Loading -> Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer),
        )
        AppScreen.Welcome -> WelcomeScreen(
            onTurnOn = viewModel::onWelcomeTurnOn,
            onNotNow = viewModel::onWelcomeNotNow,
        )
        AppScreen.UsageAccessGuide -> {
            BackHandler(onBack = viewModel::closeUsageAccessGuide)
            UsageAccessScreen(
                onOpenSettings = onOpenUsageAccessSettings,
                onBack = viewModel::closeUsageAccessGuide,
            )
        }
        AppScreen.Home -> HomeScreen(
            status = state.homeStatus,
            todayUsage = state.todayUsage,
            historyLength = state.historyLength,
            onTrackingChange = viewModel::setTrackingEnabled,
            onAllowUsageAccess = viewModel::openUsageAccessGuide,
            onHistoryLengthChange = viewModel::setHistoryLength,
        )
    }
}
