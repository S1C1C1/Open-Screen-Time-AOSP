package org.openscreentime.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.openscreentime.R
import org.openscreentime.blocking.LimitMode

val LimitMode.titleRes: Int
    get() = when (this) {
        LimitMode.Strict -> R.string.mode_strict
        LimitMode.Grace -> R.string.mode_grace
        LimitMode.Reminder -> R.string.mode_reminder
    }

val LimitMode.descriptionRes: Int
    get() = when (this) {
        LimitMode.Strict -> R.string.mode_strict_description
        LimitMode.Grace -> R.string.mode_grace_description
        LimitMode.Reminder -> R.string.mode_reminder_description
    }

/** Chooses what happens when time is up, for all apps and downtime. */
@Composable
fun LimitModeDialog(selected: LimitMode, onSelect: (LimitMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mode_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                LimitMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = mode == selected, role = Role.RadioButton) { onSelect(mode) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        RadioButton(selected = mode == selected, onClick = null)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(stringResource(mode.titleRes), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(mode.descriptionRes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
