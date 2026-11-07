package org.openscreentime.ui.schedules

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openscreentime.R
import org.openscreentime.groups.AppGroup
import org.openscreentime.schedules.AppSchedule
import org.openscreentime.schedules.SchedulePreset
import org.openscreentime.schedules.ScheduleRules
import org.openscreentime.ui.components.AppIcon
import org.openscreentime.ui.components.FootnoteText
import org.openscreentime.ui.components.SettingsCard
import org.openscreentime.ui.components.SettingsScaffold
import org.openscreentime.ui.downtime.TimePickerDialog
import org.openscreentime.ui.format.formatDays
import org.openscreentime.ui.format.formatTime
import org.openscreentime.ui.format.formatWindow
import org.openscreentime.ui.limits.SwitchRow
import org.openscreentime.usage.InstalledApp
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle

/** The name shown for a preset, in the phone's language. */
@Composable
fun presetName(preset: SchedulePreset): String = stringResource(
    when (preset) {
        SchedulePreset.WorkSchool -> R.string.schedule_preset_work
        SchedulePreset.Bedtime -> R.string.schedule_preset_bedtime
    },
)

/** Saved schedules, the Work or school and Bedtime presets, and a way to make a custom one. */
@Composable
fun ScheduleListScreen(
    schedules: List<AppSchedule>,
    daysInOrder: List<DayOfWeek>,
    pinSet: Boolean,
    onBack: () -> Unit,
    onScheduleClick: (Long) -> Unit,
    onNewSchedule: () -> Unit,
    onPresetClick: (SchedulePreset, String) -> Unit,
    onSetPinClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
    // Hide a preset once a schedule with its name exists.
    val taken = schedules.map { it.name.lowercase() }.toSet()
    val presets = SchedulePreset.entries.map { it to presetName(it) }.filter { (_, name) -> name.lowercase() !in taken }

    SettingsScaffold(title = stringResource(R.string.schedules_title), onBack = onBack, modifier = modifier) {
        Text(
            stringResource(R.string.schedules_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (!pinSet) PinReminder(onSetPinClick)
        SettingsCard {
            schedules.forEach { schedule ->
                val summary = stringResource(
                    R.string.schedules_summary,
                    formatDays(schedule.days, daysInOrder, resources, locale),
                    formatWindow(schedule.window, resources, locale, use24Hour),
                )
                ListItem(
                    modifier = Modifier.clickable { onScheduleClick(schedule.id) },
                    headlineContent = { Text(schedule.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(if (schedule.enabled) summary else stringResource(R.string.schedules_summary_off, summary))
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
            }
            AddRow(stringResource(R.string.schedules_new), onNewSchedule)
        }
        if (presets.isNotEmpty()) {
            SectionTitle(stringResource(R.string.schedules_presets))
            SettingsCard {
                presets.forEachIndexed { index, (preset, name) ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                    ListItem(
                        modifier = Modifier.clickable { onPresetClick(preset, name) },
                        headlineContent = { Text(name) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.schedules_summary,
                                    formatDays(preset.days, daysInOrder, resources, locale),
                                    formatWindow(preset.window, resources, locale, use24Hour),
                                ),
                            )
                        },
                        trailingContent = {
                            Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
                    )
                }
            }
            FootnoteText(stringResource(R.string.schedules_preset_note))
        }
    }
}

/**
 * Edits a schedule's name, days, times and what it blocks. Nothing is saved
 * until Save is tapped, which may ask for the PIN.
 */
@Composable
fun ScheduleEditorScreen(
    draft: ScheduleDraft,
    daysInOrder: List<DayOfWeek>,
    groups: List<AppGroup>,
    labelFor: (String) -> String,
    onChange: ((ScheduleDraft) -> ScheduleDraft) -> Unit,
    onChooseTargets: () -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pickingStart by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val title = if (draft.isNew) stringResource(R.string.schedules_new) else draft.name.ifBlank { stringResource(R.string.schedules_title) }

    SettingsScaffold(
        title = title,
        onBack = onBack,
        modifier = modifier,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                if (!draft.isNew) {
                    TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.schedules_delete)) }
                }
                Button(onClick = onSave, enabled = draft.canSave) { Text(stringResource(R.string.limits_save)) }
            }
        },
    ) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { typed -> onChange { it.copy(name = typed.take(ScheduleRules.MAX_NAME_LENGTH)) } },
            label = { Text(stringResource(R.string.schedules_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        SettingsCard {
            SwitchRow(stringResource(R.string.schedules_switch), draft.enabled) { on -> onChange { it.copy(enabled = on) } }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.schedules_days), style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    daysInOrder.forEach { day ->
                        val fullName = day.getDisplayName(TextStyle.FULL, locale)
                        FilledIconToggleButton(
                            checked = day in draft.days,
                            onCheckedChange = { onChange { it.toggleDay(day) } },
                            modifier = Modifier
                                .size(40.dp)
                                .semantics { contentDescription = fullName },
                        ) {
                            Text(day.getDisplayName(TextStyle.NARROW, locale))
                        }
                    }
                }
                if (draft.days.isEmpty()) {
                    Text(
                        stringResource(R.string.schedules_no_days),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
            TimeRow(stringResource(R.string.downtime_starts), formatTime(draft.start, locale, use24Hour)) { pickingStart = true }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
            TimeRow(
                stringResource(if (draft.crossesMidnight) R.string.downtime_ends_next_day else R.string.downtime_ends),
                formatTime(draft.end, locale, use24Hour),
            ) { pickingStart = false }
        }
        FootnoteText(stringResource(R.string.schedules_note))

        SectionTitle(stringResource(R.string.schedules_blocks_header))
        SettingsCard {
            val chosenGroups = groups.filter { it.id in draft.groupIds }
            val chosenApps = draft.packages.map { it to labelFor(it) }.sortedBy { it.second.lowercase() }
            if (chosenGroups.isEmpty() && chosenApps.isEmpty()) {
                Text(
                    stringResource(R.string.schedules_nothing_blocked),
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            chosenGroups.forEach { group ->
                TargetRow(
                    title = group.name,
                    leading = {
                        Icon(
                            painterResource(R.drawable.ic_apps),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    },
                    onRemove = { onChange { it.toggleGroup(group.id) } },
                )
            }
            chosenApps.forEach { (pkg, label) ->
                TargetRow(title = label, leading = { AppIcon(pkg, label) }, onRemove = { onChange { it.toggleApp(pkg) } })
            }
            AddRow(stringResource(R.string.schedules_choose), onChooseTargets)
        }
    }

    pickingStart?.let { start ->
        TimePickerDialog(
            title = stringResource(if (start) R.string.downtime_starts else R.string.downtime_ends),
            initial = if (start) draft.start else draft.end,
            use24Hour = use24Hour,
            onDismiss = { pickingStart = null },
            onConfirm = { time: LocalTime ->
                pickingStart = null
                // Start and end can't be the same; ignore a pick that would make them equal.
                onChange { if (start && time != it.end) it.copy(start = time) else if (!start && time != it.start) it.copy(end = time) else it }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.schedules_delete_title, draft.name)) },
            text = { Text(stringResource(R.string.schedules_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.groups_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.groups_cancel)) }
            },
        )
    }
}

/** Ticks groups and apps in or out of a schedule. Changes go into the draft straight away. */
@Composable
fun ScheduleTargetPickerScreen(
    groups: List<AppGroup>,
    apps: List<InstalledApp>,
    selectedGroups: Set<Long>,
    selectedApps: Set<String>,
    onToggleGroup: (Long) -> Unit,
    onToggleApp: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Searching for an app name is not sensitive, so the normal keyboard is fine here.
    var query by rememberSaveable { mutableStateOf("") }
    val shownGroups = groups.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val shownApps = apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    SettingsScaffold(
        title = stringResource(R.string.schedules_choose),
        onBack = onDone,
        modifier = modifier,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(onClick = onDone) { Text(stringResource(R.string.groups_done)) }
            }
        },
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.limits_search)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
        )
        if (shownGroups.isNotEmpty()) {
            SectionTitle(stringResource(R.string.schedules_groups_header))
            SettingsCard {
                shownGroups.forEach { group ->
                    CheckRow(group.name, group.id in selectedGroups, { onToggleGroup(group.id) }) {
                        Icon(
                            painterResource(R.drawable.ic_apps),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            }
        }
        SectionTitle(stringResource(R.string.schedules_apps_header))
        SettingsCard {
            if (shownApps.isEmpty()) {
                Text(
                    stringResource(R.string.limits_no_apps_found),
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            shownApps.forEach { app ->
                CheckRow(app.label, app.packageName in selectedApps, { onToggleApp(app.packageName) }) {
                    AppIcon(app.packageName, app.label)
                }
            }
        }
    }
}

@Composable
private fun CheckRow(title: String, checked: Boolean, onToggle: () -> Unit, leading: @Composable () -> Unit) {
    ListItem(
        modifier = Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        leadingContent = leading,
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
    )
}

@Composable
private fun TargetRow(title: String, leading: @Composable () -> Unit, onRemove: () -> Unit) {
    ListItem(
        leadingContent = leading,
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.schedules_remove, title))
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
}

@Composable
private fun TimeRow(title: String, time: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        headlineContent = { Text(title) },
        trailingContent = {
            Text(time, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun PinReminder(onSetPinClick: () -> Unit) {
    SettingsCard {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painter = painterResource(R.drawable.ic_lock), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(stringResource(R.string.schedules_pin_reminder), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onSetPinClick) { Text(stringResource(R.string.limits_set_pin)) }
            }
        }
    }
}

@Composable
private fun AddRow(text: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        },
        headlineContent = { Text(text, color = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
    )
}
