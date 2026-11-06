package org.openscreentime.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.openscreentime.R
import org.openscreentime.admin.EnforcementMode
import org.openscreentime.admin.SET_DEVICE_OWNER_COMMAND
import org.openscreentime.ui.components.FootnoteText
import org.openscreentime.ui.components.SettingsCard
import org.openscreentime.ui.components.SettingsScaffold

/** Whether apps can really be blocked, how to set that up, and how to undo it. */
@Composable
fun BlockingStatusScreen(
    mode: EnforcementMode,
    onBack: () -> Unit,
    onRemoveDeviceOwner: () -> Unit,
    modifier: Modifier = Modifier,
    pinSet: Boolean = false,
) {
    var confirmRemove by rememberSaveable { mutableStateOf(false) }

    SettingsScaffold(title = stringResource(R.string.blocking_title), onBack = onBack, modifier = modifier) {
        SettingsCard {
            Row(
                Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    painter = painterResource(if (mode == EnforcementMode.DeviceOwner) R.drawable.ic_shield else R.drawable.ic_warning),
                    contentDescription = null,
                    tint = if (mode == EnforcementMode.DeviceOwner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(if (mode == EnforcementMode.DeviceOwner) R.string.blocking_active else R.string.blocking_preview_only),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(
                            if (mode == EnforcementMode.DeviceOwner) R.string.blocking_active_body else R.string.blocking_preview_body,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (mode == EnforcementMode.PreviewOnly) {
            SettingsCard {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.blocking_setup_title), style = MaterialTheme.typography.titleMedium)
                    listOf(
                        R.string.blocking_setup_step_1,
                        R.string.blocking_setup_step_2,
                        R.string.blocking_setup_step_3,
                        R.string.blocking_setup_step_4,
                    ).forEachIndexed { index, step ->
                        Text("${index + 1}. ${stringResource(step)}", style = MaterialTheme.typography.bodyMedium)
                    }
                    // Selectable, so it can be copied.
                    SelectionContainer {
                        Text(
                            SET_DEVICE_OWNER_COMMAND,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small)
                                .padding(12.dp),
                        )
                    }
                }
            }
            FootnoteText(stringResource(R.string.blocking_setup_footnote))
        } else {
            SettingsCard {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.protection_title), style = MaterialTheme.typography.titleMedium)
                    if (pinSet) {
                        listOf(
                            R.string.protection_uninstall,
                            R.string.protection_force_stop,
                            R.string.protection_notifications,
                            R.string.protection_safe_mode,
                        ).forEach { Text("• " + stringResource(it), style = MaterialTheme.typography.bodyMedium) }
                    } else {
                        Text(
                            stringResource(R.string.protection_needs_pin),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = { confirmRemove = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.blocking_remove)) }
            FootnoteText(stringResource(R.string.blocking_remove_footnote))
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.blocking_remove_confirm_title)) },
            text = { Text(stringResource(R.string.blocking_remove_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemoveDeviceOwner()
                }) { Text(stringResource(R.string.blocking_remove_confirm_button), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
