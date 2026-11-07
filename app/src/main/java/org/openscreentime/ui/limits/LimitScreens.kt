package org.openscreentime.ui.limits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openscreentime.R
import org.openscreentime.limits.AppLimit
import org.openscreentime.limits.LimitRules
import org.openscreentime.ui.components.AppIcon
import org.openscreentime.ui.components.FootnoteText
import org.openscreentime.ui.components.SettingsCard
import org.openscreentime.ui.components.SettingsScaffold
import org.openscreentime.ui.format.formatDuration
import org.openscreentime.ui.format.formatLimit
import org.openscreentime.usage.InstalledApp
import java.time.Duration

/** A limited app, with the name shown to the user. */
data class LimitedApp(val limit: AppLimit, val label: String)

/** All apps with limits, and a way to add more. */
@Composable
fun LimitListScreen(
    apps: List<LimitedApp>,
    pinSet: Boolean,
    onBack: () -> Unit,
    onAppClick: (String) -> Unit,
    onAddClick: () -> Unit,
    onSetPinClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    SettingsScaffold(title = stringResource(R.string.limits_title), onBack = onBack, modifier = modifier) {
        if (!pinSet) {
            SettingsCard {
                Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lock),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.limits_pin_reminder), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = onSetPinClick, modifier = Modifier.padding(start = 0.dp)) {
                            Text(stringResource(R.string.limits_set_pin))
                        }
                    }
                }
            }
        }
        SettingsCard {
            apps.forEach { app ->
                ListItem(
                    modifier = Modifier.clickable { onAppClick(app.limit.packageName) },
                    leadingContent = { AppIcon(app.limit.packageName, app.label) },
                    headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(formatLimit(app.limit, resources)) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
            }
            ListItem(
                modifier = Modifier.clickable(onClick = onAddClick),
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                },
                headlineContent = {
                    Text(stringResource(R.string.limits_add), color = MaterialTheme.colorScheme.primary)
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
            )
        }
        FootnoteText(stringResource(R.string.limits_reset_note))
    }
}

/** Picks an app to add a limit for. Apps that already have one aren't listed. */
@Composable
fun AppPickerScreen(
    apps: List<InstalledApp>,
    onBack: () -> Unit,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Searching for an app name is not sensitive, so the normal keyboard is fine here.
    var query by rememberSaveable { mutableStateOf("") }
    val shown = apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    SettingsScaffold(title = stringResource(R.string.limits_choose_app), onBack = onBack, modifier = modifier) {
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
                ListItem(
                    modifier = Modifier.clickable { onAppClick(app.packageName) },
                    leadingContent = { AppIcon(app.packageName, app.label) },
                    headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
                )
            }
        }
    }
}

/**
 * Sets or removes one app's limit. Nothing is saved until Save is tapped,
 * which may ask for the PIN.
 */
@Composable
fun LimitEditorScreen(
    packageName: String,
    label: String,
    current: AppLimit?,
    onBack: () -> Unit,
    onSave: (AppLimit?) -> Unit,
    modifier: Modifier = Modifier,
    alwaysAllowed: Boolean = false,
) {
    var enabled by rememberSaveable(current) { mutableStateOf(true) }
    var weekdayMinutes by rememberSaveable(current) {
        mutableLongStateOf((current?.weekday ?: LimitRules.DEFAULT).toMinutes())
    }
    var separateWeekend by rememberSaveable(current) { mutableStateOf(current?.weekend != null) }
    var weekendMinutes by rememberSaveable(current) {
        mutableLongStateOf((current?.weekend ?: current?.weekday ?: LimitRules.DEFAULT).toMinutes())
    }

    SettingsScaffold(
        title = label,
        onBack = onBack,
        modifier = modifier,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(onClick = {
                    onSave(
                        if (enabled) {
                            AppLimit(
                                packageName = packageName,
                                weekday = Duration.ofMinutes(weekdayMinutes),
                                weekend = if (separateWeekend) Duration.ofMinutes(weekendMinutes) else null,
                            )
                        } else {
                            null
                        },
                    )
                }) { Text(stringResource(R.string.limits_save)) }
            }
        },
    ) {
        SettingsCard {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AppIcon(packageName, label, size = 48.dp)
                Text(stringResource(R.string.app_limit_title), style = MaterialTheme.typography.titleLarge)
            }
            SwitchRow(stringResource(R.string.limits_limit_this_app), enabled) { enabled = it }
        }
        if (enabled) {
            SettingsCard {
                DurationStepper(
                    title = stringResource(if (separateWeekend) R.string.limits_weekdays else R.string.limits_every_day),
                    minutes = weekdayMinutes,
                    onChange = { weekdayMinutes = it },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                SwitchRow(stringResource(R.string.limits_different_weekends), separateWeekend) { separateWeekend = it }
                if (separateWeekend) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                    DurationStepper(
                        title = stringResource(R.string.limits_weekends),
                        minutes = weekendMinutes,
                        onChange = { weekendMinutes = it },
                    )
                }
            }
            FootnoteText(stringResource(R.string.limits_reset_note))
            if (alwaysAllowed) FootnoteText(stringResource(R.string.limits_always_allowed_note))
        }
    }
}

@Composable
internal fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        headlineContent = { Text(title) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
    )
}

/** Hours and minutes, changed with − and + buttons in whole hours and 15-minute steps. */
@Composable
internal fun DurationStepper(title: String, minutes: Long, onChange: (Long) -> Unit) {
    val resources = LocalResources.current
    val min = LimitRules.MIN.toMinutes()
    val max = LimitRules.MAX.toMinutes()
    val step = LimitRules.STEP.toMinutes()
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                formatDuration(Duration.ofMinutes(minutes), resources),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        StepRow(
            label = stringResource(R.string.limits_hours),
            value = pluralStringResource(R.plurals.limits_hours_value, (minutes / 60).toInt(), minutes / 60),
            canDecrease = minutes - 60 >= min,
            canIncrease = minutes + 60 <= max,
            onDecrease = { onChange(minutes - 60) },
            onIncrease = { onChange(minutes + 60) },
        )
        StepRow(
            label = stringResource(R.string.limits_minutes),
            value = stringResource(R.string.duration_minutes, minutes % 60),
            canDecrease = minutes - step >= min,
            canIncrease = minutes + step <= max,
            onDecrease = { onChange(minutes - step) },
            onIncrease = { onChange(minutes + step) },
        )
    }
}

@Composable
private fun StepRow(
    label: String,
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedIconButton(onClick = onDecrease, enabled = canDecrease) {
            Icon(painterResource(R.drawable.ic_remove), contentDescription = stringResource(R.string.limits_less, label))
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(72.dp),
        )
        OutlinedIconButton(onClick = onIncrease, enabled = canIncrease) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.limits_more, label))
        }
    }
}

/** Small clock shown next to apps that a schedule is blocking right now. */
@Composable
fun ScheduleBadge(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_schedule),
        contentDescription = stringResource(R.string.schedules_blocking_now),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(16.dp),
    )
}

/** Small hourglass shown next to apps that have a limit. */
@Composable
fun LimitBadge(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_hourglass),
        contentDescription = stringResource(R.string.limits_has_limit),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(16.dp),
    )
}
