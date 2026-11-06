package org.openscreentime.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.openscreentime.ui.appdetail.AppDetailScreen
import org.openscreentime.ui.home.HomeScreen
import org.openscreentime.ui.usageaccess.UsageAccessScreen
import org.openscreentime.ui.welcome.WelcomeScreen

/**
 * Shows the right screen for the current state: welcome and usage access
 * setup first, then the dashboard and the screens reached from it.
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
        AppScreen.Home -> MainNavigation(viewModel, state)
    }
}

/** The dashboard and the screens reached from it. Back returns to the previous one. */
@Composable
private fun MainNavigation(viewModel: MainViewModel, state: AppUiState) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            HomeScreen(
                status = state.homeStatus,
                dashboard = state.dashboard,
                historyLength = state.historyLength,
                onTrackingChange = viewModel::setTrackingEnabled,
                onAllowUsageAccess = viewModel::openUsageAccessGuide,
                onHistoryLengthChange = viewModel::setHistoryLength,
                onSelectDay = viewModel::selectDay,
                onAppClick = { packageName -> navController.navigate(Routes.appDetail(packageName)) },
            )
        }
        composable(
            route = Routes.APP_DETAIL,
            arguments = listOf(navArgument(Routes.ARG_PACKAGE) { type = NavType.StringType }),
        ) { entry ->
            val packageName = entry.arguments?.getString(Routes.ARG_PACKAGE).orEmpty()
            val detail by remember(packageName) { viewModel.appDetail(packageName) }
                .collectAsStateWithLifecycle(initialValue = null)
            AppDetailScreen(detail = detail, onBack = { navController.popBackStack() })
        }
    }
}

private object Routes {
    const val DASHBOARD = "dashboard"
    const val ARG_PACKAGE = "packageName"
    const val APP_DETAIL = "app/{$ARG_PACKAGE}"

    fun appDetail(packageName: String) = "app/${Uri.encode(packageName)}"
}
