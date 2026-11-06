package org.openscreentime

import android.content.ActivityNotFoundException
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import org.openscreentime.ui.MainViewModel
import org.openscreentime.ui.OpenScreenTimeApp
import org.openscreentime.ui.theme.OpenScreenTimeTheme
import org.openscreentime.usage.usageAccessListIntent
import org.openscreentime.usage.usageAccessSettingsIntent

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.factory(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenScreenTimeTheme {
                OpenScreenTimeApp(
                    viewModel = viewModel,
                    onOpenUsageAccessSettings = ::openUsageAccessSettings,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have just changed usage access in Settings, and usage
        // has changed since the app was last open.
        viewModel.onAppVisible()
    }

    private fun openUsageAccessSettings() {
        try {
            startActivity(usageAccessSettingsIntent(packageName))
        } catch (_: ActivityNotFoundException) {
            // Some ROMs only offer the full list of apps, not one app's page.
            startActivity(usageAccessListIntent())
        }
    }
}
