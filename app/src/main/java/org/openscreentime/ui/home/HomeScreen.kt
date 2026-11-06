package org.openscreentime.ui.home

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
import org.openscreentime.ui.AppTime
import org.openscreentime.ui.HomeStatus
import org.openscreentime.ui.TodayUsage
import org.openscreentime.ui.components.FootnoteText
import org.openscreentime.ui.components.SettingsCard
import org.openscreentime.ui.components.SettingsScaffold
import org.openscreentime.ui.format.formatDuration
import org.openscreentime.ui.theme.OpenScreenTimeTheme
import java.time.Duration

/** Main screen. The full usage dashboard replaces the "Today" card in a later step. */
@Composable
fun HomeScreen(
    status: HomeStatus,
    todayUsage: TodayUsage?,
    historyLength: HistoryLength,
    onTrackingChange: (Boolean) -> Unit,
    onAllowUsageAccess: () -> Unit,
    onHistoryLengthChange: (HistoryLength) -> Unit,
    modifier: Modifier = Modifier,
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
            HomeStatus.Ready -> TodayCard(todayUsage)
        }
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

/** Temporary summary so testers can check the numbers before the full dashboard. */
@Composable
private fun TodayCard(todayUsage: TodayUsage?) {
    val resources = LocalResources.current
    SettingsCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.today_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatDuration(todayUsage?.total ?: Duration.ZERO, resources),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            if (todayUsage == null || todayUsage.topApps.isEmpty()) {
                Text(
                    text = stringResource(R.string.today_no_usage),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.today_most_used),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                todayUsage.topApps.forEach { app -> AppTimeRow(app, formatDuration(app.time, resources)) }
            }
            Text(
                text = stringResource(R.string.today_dashboard_coming),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun AppTimeRow(app: AppTime, formattedTime: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formattedTime,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    OpenScreenTimeTheme(dynamicColor = false) {
        HomeScreen(
            status = HomeStatus.Ready,
            todayUsage = TodayUsage(
                total = Duration.ofMinutes(83),
                topApps = listOf(AppTime("Browser", Duration.ofMinutes(50)), AppTime("Camera", Duration.ofMinutes(33))),
            ),
            historyLength = HistoryLength.FourWeeks,
            onTrackingChange = {},
            onAllowUsageAccess = {},
            onHistoryLengthChange = {},
        )
    }
}
