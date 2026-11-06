package org.openscreentime.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalConfiguration
import org.openscreentime.ui.components.AppIcon
import org.openscreentime.ui.components.WeekBarChart
import org.openscreentime.ui.dashboard.Dashboard
import org.openscreentime.ui.dashboard.buildDashboard
import org.openscreentime.ui.format.formatDayWithDate
import org.openscreentime.usage.AppUsageTime
import java.time.DayOfWeek
import java.time.LocalDate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.openscreentime.R
import org.openscreentime.data.HistoryLength
import org.openscreentime.ui.HomeStatus
import org.openscreentime.ui.components.FootnoteText
import org.openscreentime.ui.components.SettingsCard
import org.openscreentime.ui.components.SettingsScaffold
import org.openscreentime.ui.format.formatDuration
import org.openscreentime.ui.theme.OpenScreenTimeTheme
import java.time.Duration

/** Main screen: the usage dashboard once tracking is set up, plus its settings. */
@Composable
fun HomeScreen(
    status: HomeStatus,
    dashboard: Dashboard?,
    historyLength: HistoryLength,
    onTrackingChange: (Boolean) -> Unit,
    onAllowUsageAccess: () -> Unit,
    onHistoryLengthChange: (HistoryLength) -> Unit,
    modifier: Modifier = Modifier,
    pinSet: Boolean = false,
    onSelectDay: (LocalDate) -> Unit = {},
    onAppClick: (String) -> Unit = {},
    onPinClick: () -> Unit = {},
) {
    var showHistoryDialog by rememberSaveable { mutableStateOf(false) }

    SettingsScaffold(title = stringResource(R.string.home_title), modifier = modifier) {
        when (status) {
            HomeStatus.TrackingOff -> StatusCard(
                title = stringResource(R.string.home_tracking_off_title),
                body = stringResource(R.string.home_tracking_off_body),
                buttonText = stringResource(R.string.home_turn_on),
                onButtonClick = { onTrackingChange(true) },
            )
            HomeStatus.NeedsUsageAccess -> StatusCard(
                title = stringResource(R.string.home_needs_access_title),
                body = stringResource(R.string.home_needs_access_body),
                buttonText = stringResource(R.string.home_allow_access),
                onButtonClick = onAllowUsageAccess,
                isWarning = true,
            )
            HomeStatus.Ready -> if (dashboard != null) {
                SummaryCard(dashboard, onSelectDay)
                AppListCard(dashboard, onAppClick)
            }
        }
        SectionHeader(stringResource(R.string.dashboard_settings_header))
        SettingsCard {
            TrackingSwitchRow(
                checked = status != HomeStatus.TrackingOff,
                onCheckedChange = onTrackingChange,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
            SettingsRow(
                title = stringResource(R.string.history_title),
                summary = stringResource(historyLength.labelRes),
                onClick = { showHistoryDialog = true },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
            SettingsRow(
                title = stringResource(R.string.pin_setting_title),
                summary = stringResource(if (pinSet) R.string.pin_setting_on else R.string.pin_setting_off),
                onClick = onPinClick,
            )
        }
        FootnoteText(stringResource(R.string.home_privacy_note))
    }

    if (showHistoryDialog) {
        HistoryLengthDialog(
            selected = historyLength,
            onSelect = {
                showHistoryDialog = false
                onHistoryLengthChange(it)
            },
            onDismiss = { showHistoryDialog = false },
        )
    }
}

/** Total and week chart for the selected day. Tapping a bar selects that day. */
@Composable
private fun SummaryCard(dashboard: Dashboard, onSelectDay: (LocalDate) -> Unit) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    SettingsCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = if (dashboard.isTodaySelected) {
                    stringResource(R.string.dashboard_today)
                } else {
                    formatDayWithDate(dashboard.selectedDay, locale)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatDuration(dashboard.total, resources),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            WeekBarChart(
                bars = dashboard.week,
                selectedDay = dashboard.selectedDay,
                onDayClick = onSelectDay,
            )
        }
    }
}

/** Every app used on the selected day, most used first. */
@Composable
private fun AppListCard(dashboard: Dashboard, onAppClick: (String) -> Unit) {
    val resources = LocalResources.current
    SettingsCard {
        if (dashboard.apps.isEmpty()) {
            Text(
                text = stringResource(R.string.dashboard_no_usage),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        } else {
            dashboard.apps.forEach { app ->
                ListItem(
                    modifier = Modifier.clickable { onAppClick(app.packageName) },
                    leadingContent = { AppIcon(app.packageName, app.label) },
                    headlineContent = {
                        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    trailingContent = {
                        Text(
                            text = formatDuration(app.time, resources),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    body: String,
    buttonText: String? = null,
    onButtonClick: () -> Unit = {},
    isWarning: Boolean = false,
) {
    SettingsCard {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isWarning) {
                    Icon(
                        painter = painterResource(R.drawable.ic_warning),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                Text(text = title, style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (buttonText != null) {
                Button(onClick = onButtonClick, modifier = Modifier.padding(top = 8.dp)) {
                    Text(buttonText)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun TrackingSwitchRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        // The whole row toggles, as in AOSP Settings.
        modifier = Modifier.toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
        headlineContent = { Text(stringResource(R.string.home_use_screen_time)) },
        supportingContent = { Text(stringResource(R.string.home_use_screen_time_summary)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
    )
}

@Composable
private fun SettingsRow(title: String, summary: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.selectable(selected = false, role = Role.Button, onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
    )
}

/** A list of choices with radio buttons, like AOSP Settings' list dialogs. */
@Composable
fun HistoryLengthDialog(
    selected: HistoryLength,
    onSelect: (HistoryLength) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                Text(
                    text = stringResource(R.string.history_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                HistoryLength.entries.forEach { length ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = length == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(length) },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = length == selected, onClick = null)
                        Text(
                            text = stringResource(length.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

val HistoryLength.labelRes: Int
    get() = when (this) {
        HistoryLength.OneWeek -> R.string.history_one_week
        HistoryLength.FourWeeks -> R.string.history_four_weeks
        HistoryLength.ThreeMonths -> R.string.history_three_months
        HistoryLength.OneYear -> R.string.history_one_year
    }

@Preview
@Composable
private fun HomeScreenPreview() {
    val today = LocalDate.of(2026, 11, 5)
    OpenScreenTimeTheme(dynamicColor = false) {
        HomeScreen(
            status = HomeStatus.Ready,
            dashboard = buildDashboard(
                usageByDay = mapOf(today to listOf(AppUsageTime("browser", Duration.ofMinutes(50)))),
                today = today,
                selectedDay = null,
                firstDayOfWeek = DayOfWeek.MONDAY,
                labels = { "Browser" },
            ),
            historyLength = HistoryLength.FourWeeks,
            onTrackingChange = {},
            onAllowUsageAccess = {},
            onHistoryLengthChange = {},
        )
    }
}
