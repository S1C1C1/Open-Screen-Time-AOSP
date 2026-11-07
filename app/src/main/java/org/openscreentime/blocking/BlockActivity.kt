package org.openscreentime.blocking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openscreentime.OpenScreenTimeApplication
import org.openscreentime.ui.MainViewModel
import org.openscreentime.ui.block.BlockActions
import org.openscreentime.ui.block.BlockScreen
import org.openscreentime.ui.pin.PinFlowActions
import org.openscreentime.ui.pin.PinFlowScreen
import org.openscreentime.ui.theme.OpenScreenTimeTheme
import org.openscreentime.usage.LocalAppIcons

/**
 * The block screen shown over an app when its limit is reached or
 * downtime starts (device-owner mode). "Close app" goes to the home screen.
 */
class BlockActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.factory(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showFor(intent)
        val icons = (application as OpenScreenTimeApplication).container.appIcons
        setContent {
            OpenScreenTimeTheme {
                CompositionLocalProvider(LocalAppIcons provides icons) {
                    val block by viewModel.blockScreenUi.collectAsStateWithLifecycle()
                    val pin by viewModel.pinFlow.ui.collectAsStateWithLifecycle()
                    var opened by remember { mutableStateOf(false) }
                    // Back closes the PIN screen if open, otherwise leaves the blocked app like "Close app".
                    BackHandler { if (pin != null) viewModel.pinFlow.cancel() else goHome() }
                    LaunchedEffect(block) {
                        if (block != null) opened = true else if (opened) finish()
                    }
                    block?.let { ui ->
                        BlockScreen(
                            ui = ui,
                            actions = BlockActions(
                                onClose = ::goHome,
                                onOneMoreMinute = viewModel::blockOneMoreMinute,
                                onIgnore = viewModel::blockIgnore,
                                onEnterPin = viewModel::blockEnterPin,
                                onUnlock = viewModel::blockUnlock,
                            ),
                        )
                    }
                    pin?.let { ui ->
                        val flow = viewModel.pinFlow
                        PinFlowScreen(
                            ui = ui,
                            actions = PinFlowActions(
                                onDigit = flow::addDigit,
                                onDelete = flow::deleteDigit,
                                onSubmit = viewModel::submitPin,
                                onContinueIntro = flow::continueFromIntro,
                                onWaitFinished = flow::waitFinished,
                                onClose = flow::cancel,
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        showFor(intent)
    }

    private fun showFor(intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return finish()
        val reason = intent.getStringExtra(EXTRA_REASON)?.let { BlockReason.valueOf(it) } ?: BlockReason.LimitReached
        viewModel.openBlockScreen(
            pkg,
            reason,
            real = true,
            ruleName = intent.getStringExtra(EXTRA_RULE),
            overrideKey = intent.getStringExtra(EXTRA_OVERRIDE_KEY),
        )
    }

    /** Sends the user to the home screen, away from the blocked app. */
    private fun goHome() {
        viewModel.closeBlockScreen()
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_REASON = "reason"
        private const val EXTRA_RULE = "rule"
        private const val EXTRA_OVERRIDE_KEY = "override_key"

        fun intent(
            context: Context,
            packageName: String,
            reason: BlockReason,
            ruleName: String? = null,
            overrideKey: String? = null,
        ): Intent =
            Intent(context, BlockActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_REASON, reason.name)
                .putExtra(EXTRA_RULE, ruleName)
                .putExtra(EXTRA_OVERRIDE_KEY, overrideKey)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
