package org.openscreentime.ui.downtime

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.openscreentime.R
import org.openscreentime.downtime.DowntimeSchedule
import org.openscreentime.downtime.DowntimeStatus
import org.openscreentime.downtime.DowntimeWindow
import org.openscreentime.ui.components.SettingsCard
import org.openscreentime.ui.components.SettingsScaffold
import org.openscreentime.ui.format.formatDowntimeStatus
import org.openscreentime.ui.format.formatTime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle

/** Which time of a window is being changed. */
private enum class Edge { Start, End }

/** The time picker that's open: for every day (day == null) or one day. */
private data class PickerTarget(val day: DayOfWeek?, val edge: Edge)

/**
 * Downtime settings. Each change is passed to [onChange] straight away,
 * like AOSP Settings; the caller asks for the PIN when one is set.
 */
@Composable
fun DowntimeScreen(
    schedule: DowntimeSchedule,
    status: DowntimeStatus,
    today: LocalDate,
    daysInOrder: List<DayOfWeek>,
    pinSet: Boolean,
    onChange: (DowntimeSchedule) -> Unit,
    onBack: () -> Unit,
    onSetPinClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPreviewBlock: () -> Unit = {},
) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
    var picker by remember { mutableStateOf<PickerTarget?>(null) }

    SettingsScaffold(title = stringResource(R.string.downtime_title), onBack = onBack, modifier = modifier) {
        Text(
            stringResource(R.string.downtime_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (!pinSet) PinReminder(onSetPinClick)

        SettingsCard {
            SwitchRow(
                title = stringResource(R.string.downtime_switch),
                summary = formatDowntimeStatus(status, today, resources, locale, use24Hour),
                checked = schedule.enabled,
                onChange = { onChange(schedule.copy(enabled = it)) },
            )
        }

        if (schedule.enabled) {
            ModeSelector(
                sameEveryDay = schedule.sameEveryDay,
                onChange = { everyDay ->
                    if (everyDay) {
                        // Use the first day that has downtime as the window for every day.
                        val window = daysInOrder.firstNotNullOfOrNull(schedule::windowFor) ?: DowntimeWindow.DEFAULT
                        onChange(schedule.withEveryDay(window))
                    } else {
                        onChange(schedule.copy(sameEveryDay = false))
                    }
                },
            )
            if (schedule.sameEveryDay) {
                val window = daysInOrder.firstNotNullOfOrNull(schedule::windowFor) ?: DowntimeWindow.DEFAULT
                SettingsCard {
                    TimeRow(stringResource(R.string.downtime_starts), formatTime(window.start, locale, use24Hour)) {
                        picker = PickerTarget(null, Edge.Start)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                    TimeRow(
                        stringResource(if (window.crossesMidnight) R.string.downtime_ends_next_day else R.string.downtime_ends),
                        formatTime(window.end, locale, use24Hour),
                    ) { picker = PickerTarget(null, Edge.End) }
                }
            } else {
                SettingsCard {
                    daysInOrder.forEachIndexed { index, day ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                        DayRow(
                            day = day,
                            window = schedule.windowFor(day),
                            formatTime = { formatTime(it, locale, use24Hour) },
                            onToggle = { on ->
                                onChange(schedule.withDay(day, if (on) DowntimeWindow.DEFAULT else null))
                            },
                            onPick = { edge -> picker = PickerTarget(day, edge) },
                        )
                    }
                }
            }
            androidx.compose.material3.OutlinedButton(onClick = onPreviewBlock) {
                Text(stringResource(R.string.block_preview))
            }
        }
    }

    picker?.let { target ->
        val window = (if (target.day == null) daysInOrder.firstNotNullOfOrNull(schedule::windowFor) else schedule.windowFor(target.day))
            ?: DowntimeWindow.DEFAULT
        val initial = if (target.edge == Edge.Start) window.start else window.end
        TimePickerDialog(
            title = stringResource(if (target.edge == Edge.Start) R.string.downtime_starts else R.string.downtime_ends),
            initial = initial,
            use24Hour = use24Hour,
            onDismiss = { picker = null },
            onConfirm = { time ->
                picker = null
                val updated = if (target.edge == Edge.Start) window.copyOrNull(start = time) else window.copyOrNull(end = time)
                if (updated != null) {
                    onChange(if (target.day == null) schedule.withEveryDay(updated) else schedule.withDay(target.day, updated))
                }
            },
        )
    }
}

/** A new window with one time changed, or null if start and end would be equal. */
private fun DowntimeWindow.copyOrNull(start: LocalTime = this.start, end: LocalTime = this.end): DowntimeWindow? =
    if (start == end) null else DowntimeWindow(start, end)

@Composable
private fun PinReminder(onSetPinClick: () -> Unit) {
    SettingsCard {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_lock),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(stringResource(R.string.downtime_pin_reminder), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onSetPinClick) { Text(stringResource(R.string.limits_set_pin)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelector(sameEveryDay: Boolean, onChange: (Boolean) -> Unit) {
    val options = listOf(true to R.string.downtime_every_day, false to R.string.downtime_custom_days)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = sameEveryDay == value,
                onClick = { onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(stringResource(label)) }
        }
    }
}

@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
    )
}

@Composable
private fun TimeRow(title: String, time: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.toggleable(value = false, role = Role.Button, onValueChange = { onClick() }),
        headlineContent = { Text(title) },
        trailingContent = {
            Text(time, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
    )
}

/** One day in "custom days": a switch, and its start and end times when on. */
@Composable
private fun DayRow(
    day: DayOfWeek,
    window: DowntimeWindow?,
    formatTime: (LocalTime) -> String,
    onToggle: (Boolean) -> Unit,
    onPick: (Edge) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = window != null, role = Role.Switch, onValueChange = onToggle),
        ) {
            Text(
                day.getDisplayName(TextStyle.FULL, locale),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = window != null, onCheckedChange = null)
        }
        if (window != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onPick(Edge.Start) }) { Text(formatTime(window.start)) }
                Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { onPick(Edge.End) }) { Text(formatTime(window.end)) }
                if (window.crossesMidnight) {
                    Text(
                        stringResource(R.string.downtime_next_day),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Android's standard clock-style time picker, in a dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    title: String,
    initial: LocalTime,
    use24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(initial.hour, initial.minute, use24Hour)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.pin_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
