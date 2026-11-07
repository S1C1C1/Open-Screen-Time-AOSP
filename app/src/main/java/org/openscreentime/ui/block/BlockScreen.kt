package org.openscreentime.ui.block

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openscreentime.R
import org.openscreentime.blocking.BlockReason
import org.openscreentime.blocking.LimitMode
import org.openscreentime.blocking.UnlockDuration
import org.openscreentime.ui.components.AppIcon
import org.openscreentime.ui.format.formatTime
import java.time.ZonedDateTime

/** What a block screen shows. */
data class BlockScreenUi(
    val packageName: String,
    /** Null when previewing downtime without a particular app. */
    val label: String?,
    val reason: BlockReason,
    val blockedUntil: ZonedDateTime,
    val mode: LimitMode,
    val graceAvailable: Boolean,
    /** After the PIN, the user picks how long to unlock for. */
    val choosingUnlock: Boolean = false,
    /** True in phase 1, where block screens can only be previewed. */
    val isPreview: Boolean = true,
    /** Set when the app is blocked by a group's shared limit rather than its own. */
    val groupName: String? = null,
)

class BlockActions(
    val onClose: () -> Unit,
    val onOneMoreMinute: () -> Unit,
    val onIgnore: () -> Unit,
    val onEnterPin: () -> Unit,
    val onUnlock: (UnlockDuration) -> Unit,
)

/**
 * Shown instead of an app when its limit is reached or during downtime.
 * The buttons depend on the chosen mode: Strict (PIN only), Grace (one
 * more minute once a day, then PIN) or Reminder (can be ignored).
 */
@Composable
fun BlockScreen(ui: BlockScreenUi, actions: BlockActions, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (ui.isPreview) {
                Text(
                    stringResource(R.string.block_preview_banner),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium)
                        .padding(8.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (ui.choosingUnlock) UnlockChoice(ui, actions) else BlockMessage(ui, actions)
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun BlockMessage(ui: BlockScreenUi, actions: BlockActions) {
    val label = ui.label ?: stringResource(R.string.block_this_app)
    val until = formatTime(
        ui.blockedUntil.toLocalTime(),
        LocalConfiguration.current.locales[0],
        DateFormat.is24HourFormat(LocalContext.current),
    )
    Box(
        modifier = Modifier
            .size(88.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (ui.label != null) {
            AppIcon(ui.packageName, label, size = 56.dp)
        } else {
            Icon(
                painterResource(R.drawable.ic_hourglass),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp),
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    Text(
        stringResource(if (ui.reason == BlockReason.Downtime) R.string.block_downtime_title else R.string.block_limit_title),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        when {
            ui.reason == BlockReason.Downtime -> stringResource(R.string.block_downtime_body, label, until)
            ui.groupName != null -> stringResource(R.string.block_group_limit_body, ui.groupName, label)
            else -> stringResource(R.string.block_limit_body, label)
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    if (ui.mode == LimitMode.Reminder) {
        Text(
            stringResource(R.string.block_reminder_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    Spacer(Modifier.height(32.dp))
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(onClick = actions.onClose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.block_close))
        }
        when (ui.mode) {
            LimitMode.Reminder -> OutlinedButton(onClick = actions.onIgnore, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.block_ignore))
            }
            LimitMode.Grace, LimitMode.Strict -> {
                if (ui.mode == LimitMode.Grace && ui.graceAvailable) {
                    FilledTonalButton(onClick = actions.onOneMoreMinute, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.block_one_more_minute))
                    }
                }
                TextButton(onClick = actions.onEnterPin) { Text(stringResource(R.string.block_enter_pin)) }
            }
        }
    }
}

@Composable
private fun UnlockChoice(ui: BlockScreenUi, actions: BlockActions) {
    val label = ui.label ?: stringResource(R.string.block_this_app)
    Text(
        stringResource(R.string.unlock_title, label),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            UnlockDuration.FifteenMinutes to R.string.unlock_15_minutes,
            UnlockDuration.OneHour to R.string.unlock_1_hour,
            UnlockDuration.RestOfDay to
                if (ui.reason == BlockReason.Downtime) R.string.unlock_until_downtime_ends else R.string.unlock_rest_of_day,
        ).forEach { (duration, text) ->
            FilledTonalButton(onClick = { actions.onUnlock(duration) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(text))
            }
        }
        TextButton(onClick = actions.onClose, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.cancel))
        }
    }
}
