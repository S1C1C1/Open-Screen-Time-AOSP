package org.openscreentime.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openscreentime.R
import org.openscreentime.groups.AppGroup
import org.openscreentime.groups.GroupRules
import org.openscreentime.groups.GroupSuggestion
import org.openscreentime.ui.components.AppIcon
import org.openscreentime.ui.components.FootnoteText
import org.openscreentime.ui.components.SettingsCard
import org.openscreentime.ui.components.SettingsScaffold
import org.openscreentime.ui.format.formatLimit
import org.openscreentime.ui.limits.DurationStepper
import org.openscreentime.ui.limits.SwitchRow
import org.openscreentime.usage.InstalledApp

/** A suggested group with its name already looked up, ready to show. */
data class SuggestionUi(val name: String, val packages: Set<String>)

/** Saved groups, suggestions from app categories, and a way to make a custom group. */
@Composable
fun GroupListScreen(
    groups: List<AppGroup>,
    suggestions: List<GroupSuggestion>,
    pinSet: Boolean,
    onBack: () -> Unit,
    onGroupClick: (Long) -> Unit,
    onNewGroup: () -> Unit,
    onSuggestionClick: (SuggestionUi) -> Unit,
    onSetPinClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    // Hide a suggestion once a group with the same name exists.
    val taken = groups.map { it.name.lowercase() }.toSet()
    val shownSuggestions = suggestions
        .map { SuggestionUi(resources.getString(it.nameRes), it.packages) }
        .filter { it.name.lowercase() !in taken }

    SettingsScaffold(title = stringResource(R.string.groups_title), onBack = onBack, modifier = modifier) {
        Text(
            stringResource(R.string.groups_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (!pinSet) PinReminder(onSetPinClick)
        SettingsCard {
            groups.forEach { group ->
                ListItem(
                    modifier = Modifier.clickable { onGroupClick(group.id) },
                    headlineContent = { Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        val apps = pluralStringResource(R.plurals.groups_app_count, group.packages.size, group.packages.size)
                        val limit = group.limit?.let { formatLimit(it, resources) } ?: stringResource(R.string.groups_no_limit)
                        Text(stringResource(R.string.groups_summary, apps, limit))
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
            }
            AddRow(stringResource(R.string.groups_new), onNewGroup)
        }
        if (shownSuggestions.isNotEmpty()) {
            Text(
                stringResource(R.string.groups_suggested),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
            SettingsCard {
                shownSuggestions.forEachIndexed { index, suggestion ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                    ListItem(
                        modifier = Modifier.clickable { onSuggestionClick(suggestion) },
                        headlineContent = { Text(suggestion.name) },
                        supportingContent = {
                            Text(pluralStringResource(R.plurals.groups_app_count, suggestion.packages.size, suggestion.packages.size))
                        },
                        trailingContent = {
                            Icon(
                                painterResource(R.drawable.ic_add),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
                    )
                }
            }
            FootnoteText(stringResource(R.string.groups_suggested_note))
        }
    }
}

/**
 * Edits a group's name, shared limit and apps. Nothing is saved until Save
 * is tapped, which may ask for the PIN.
 */
@Composable
fun GroupEditorScreen(
    draft: GroupDraft,
    labelFor: (String) -> String,
    onChange: ((GroupDraft) -> GroupDraft) -> Unit,
    onChooseApps: () -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val title = if (draft.isNew) stringResource(R.string.groups_new) else draft.name.ifBlank { stringResource(R.string.groups_title) }

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
                    TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.groups_delete)) }
                }
                Button(onClick = onSave, enabled = draft.canSave) { Text(stringResource(R.string.limits_save)) }
            }
        },
    ) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { typed -> onChange { it.copy(name = typed.take(GroupRules.MAX_NAME_LENGTH)) } },
            label = { Text(stringResource(R.string.groups_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        SettingsCard {
            SwitchRow(stringResource(R.string.groups_limit_switch), draft.limitOn) { on -> onChange { it.copy(limitOn = on) } }
            if (draft.limitOn) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                DurationStepper(
                    title = stringResource(if (draft.separateWeekend) R.string.limits_weekdays else R.string.limits_every_day),
                    minutes = draft.weekdayMinutes,
                    onChange = { minutes -> onChange { it.copy(weekdayMinutes = minutes) } },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                SwitchRow(stringResource(R.string.limits_different_weekends), draft.separateWeekend) { on ->
                    onChange { it.copy(separateWeekend = on) }
                }
                if (draft.separateWeekend) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                    DurationStepper(
                        title = stringResource(R.string.limits_weekends),
                        minutes = draft.weekendMinutes,
                        onChange = { minutes -> onChange { it.copy(weekendMinutes = minutes) } },
                    )
                }
            }
        }
        if (draft.limitOn) FootnoteText(stringResource(R.string.groups_limit_note))
        Text(
            stringResource(R.string.groups_apps_header),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
        SettingsCard {
            val members = draft.packages.map { it to labelFor(it) }.sortedBy { it.second.lowercase() }
            if (members.isEmpty()) {
                Text(
                    stringResource(R.string.groups_no_apps),
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            members.forEach { (pkg, label) ->
                ListItem(
                    leadingContent = { AppIcon(pkg, label) },
                    headlineContent = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        IconButton(onClick = { onChange { it.toggle(pkg) } }) {
                            Icon(
                                painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.groups_remove_app, label),
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
            }
            AddRow(stringResource(R.string.groups_add_apps), onChooseApps)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.groups_delete_title, draft.name)) },
            text = { Text(stringResource(R.string.groups_delete_body)) },
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

/** Ticks apps in or out of a group. Changes go into the draft straight away. */
@Composable
fun GroupAppPickerScreen(
    apps: List<InstalledApp>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Searching for an app name is not sensitive, so the normal keyboard is fine here.
    var query by rememberSaveable { mutableStateOf("") }
    val shown = apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    SettingsScaffold(
        title = stringResource(R.string.groups_choose_apps),
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
        SettingsCard {
            if (shown.isEmpty()) {
                Text(
                    stringResource(R.string.limits_no_apps_found),
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            shown.forEach { app ->
                val checked = app.packageName in selected
                ListItem(
                    modifier = Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle(app.packageName) }),
                    leadingContent = { AppIcon(app.packageName, app.label) },
                    headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
                )
            }
        }
    }
}

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
                Text(stringResource(R.string.groups_pin_reminder), style = MaterialTheme.typography.bodyMedium)
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
