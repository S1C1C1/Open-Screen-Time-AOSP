package org.openscreentime.ui.pin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.openscreentime.R
import org.openscreentime.pin.PinRules
import org.openscreentime.ui.components.SecureWindow
import org.openscreentime.ui.components.SettingsCard
import org.openscreentime.ui.format.formatDuration
import java.time.Duration

/** Callbacks from the PIN screens. */
class PinFlowActions(
    val onDigit: (Char) -> Unit,
    val onDelete: () -> Unit,
    val onSubmit: () -> Unit,
    val onContinueIntro: () -> Unit,
    val onWaitFinished: () -> Unit,
    val onClose: () -> Unit,
)

/** Shows the current PIN screen. Screenshots are blocked while it's open. */
@Composable
fun PinFlowScreen(ui: PinFlowUi, actions: PinFlowActions, modifier: Modifier = Modifier) {
    SecureWindow()
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.statusBarsPadding().navigationBarsPadding()) {
            Row(modifier = Modifier.padding(4.dp)) {
                IconButton(onClick = actions.onClose) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.navigate_back),
                    )
                }
            }
            when (ui.step) {
                PinStep.Intro -> PinIntro(ui.message, actions.onContinueIntro, actions.onClose)
                PinStep.Finished -> PinFinished(ui.kind, actions.onClose)
                else -> PinEntry(ui, actions)
            }
        }
    }
}

@Composable
private fun PinEntry(ui: PinFlowUi, actions: PinFlowActions) {
    val title = when (ui.step) {
        PinStep.EnterCurrent -> if (ui.kind == PinFlowKind.Change) R.string.pin_enter_current else R.string.pin_enter
        PinStep.EnterNew -> R.string.pin_choose
        else -> R.string.pin_confirm
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        if (ui.step == PinStep.EnterNew) {
            Text(
                text = pluralStringResource(
                    R.plurals.pin_length_hint,
                    PinRules.MAX_LENGTH,
                    PinRules.MIN_LENGTH,
                    PinRules.MAX_LENGTH,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(32.dp))
        PinDots(ui.digitCount)
        Box(modifier = Modifier.height(64.dp).padding(top = 16.dp), contentAlignment = Alignment.TopCenter) {
            PinStatusText(ui, actions.onWaitFinished)
        }
        Spacer(Modifier.weight(1f))
        PinPad(
            enabled = ui.canType,
            canDelete = !ui.busy && ui.digitCount > 0,
            canSubmit = ui.canSubmit,
            submitLabel = if (ui.step == PinStep.EnterNew) R.string.pin_next else R.string.pin_ok,
            actions = actions,
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** One dot per possible digit; filled dots show how many have been typed. */
@Composable
private fun PinDots(count: Int) {
    val description = pluralStringResource(R.plurals.pin_digits_entered, count, count)
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        repeat(PinRules.MAX_LENGTH) { index ->
            val filled = index < count
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .then(
                        if (filled) {
                            Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        },
                    ),
            )
        }
    }
}

@Composable
private fun PinStatusText(ui: PinFlowUi, onWaitFinished: () -> Unit) {
    val wait = ui.waitRemaining
    val text = when {
        wait != null -> {
            var left by remember(wait) { mutableStateOf(wait) }
            LaunchedEffect(wait) {
                while (left > Duration.ZERO) {
                    delay(1_000)
                    left = (left - Duration.ofSeconds(1)).coerceAtLeast(Duration.ZERO)
                }
                onWaitFinished()
            }
            stringResource(R.string.pin_wait, formatWait(left))
        }
        else -> when (val message = ui.message) {
            is PinMessage.WrongPin -> if (message.attemptsBeforeWait > 0) {
                pluralStringResource(R.plurals.pin_wrong_tries_left, message.attemptsBeforeWait, message.attemptsBeforeWait)
            } else {
                stringResource(R.string.pin_wrong)
            }
            PinMessage.Mismatch -> stringResource(R.string.pin_mismatch)
            PinMessage.NotSaved -> stringResource(R.string.pin_not_saved)
            null -> null
        }
    }
    if (text != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            // TalkBack reads changes to this message out loud.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** "4:05" under an hour, otherwise "3 hr 59 min". */
@Composable
private fun formatWait(left: Duration): String {
    val totalSeconds = left.seconds.coerceAtLeast(0)
    return if (totalSeconds < 3600) {
        "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    } else {
        formatDuration(left, LocalResources.current)
    }
}

/**
 * The app's own number pad. The phone's keyboard is never used for the PIN,
 * so no keyboard app can see or record it.
 */
@Composable
private fun PinPad(
    enabled: Boolean,
    canDelete: Boolean,
    canSubmit: Boolean,
    submitLabel: Int,
    actions: PinFlowActions,
) {
    val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { digit -> DigitKey(digit, enabled, actions.onDigit) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(KEY_SIZE), contentAlignment = Alignment.Center) {
                IconButton(onClick = actions.onDelete, enabled = canDelete, modifier = Modifier.size(KEY_SIZE)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_backspace),
                        contentDescription = stringResource(R.string.pin_delete),
                    )
                }
            }
            DigitKey('0', enabled, actions.onDigit)
            Box(Modifier.size(KEY_SIZE), contentAlignment = Alignment.Center) {
                TextButton(onClick = actions.onSubmit, enabled = canSubmit) {
                    Text(stringResource(submitLabel))
                }
            }
        }
    }
}

@Composable
private fun DigitKey(digit: Char, enabled: Boolean, onDigit: (Char) -> Unit) {
    FilledTonalButton(
        onClick = { onDigit(digit) },
        enabled = enabled,
        shape = CircleShape,
        modifier = Modifier.size(KEY_SIZE),
    ) {
        Text(digit.toString(), style = MaterialTheme.typography.headlineSmall)
    }
}

/** Explains the PIN before it's created, with the warnings the spec requires. */
@Composable
private fun PinIntro(message: PinMessage?, onContinue: () -> Unit, onCancel: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.pin_intro_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
            SettingsCard {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.pin_intro_body), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.pin_intro_not_unlock_code),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            SettingsCard {
                Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_warning),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        stringResource(R.string.pin_intro_forgot_warning),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            if (message == PinMessage.NotSaved) {
                Text(
                    stringResource(R.string.pin_not_saved),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            Button(onClick = onContinue) { Text(stringResource(R.string.pin_intro_continue)) }
        }
    }
}

@Composable
private fun PinFinished(kind: PinFlowKind, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lock),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            text = stringResource(if (kind == PinFlowKind.Change) R.string.pin_changed else R.string.pin_set),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = stringResource(R.string.pin_set_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
        )
        Button(onClick = onDone) { Text(stringResource(R.string.pin_done)) }
    }
}

private val KEY_SIZE = 72.dp
