package org.openscreentime.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.openscreentime.R
import org.openscreentime.ui.HomeStatus
import org.openscreentime.ui.components.FootnoteText
import org.openscreentime.ui.components.SettingsCard
import org.openscreentime.ui.components.SettingsScaffold
import org.openscreentime.ui.theme.OpenScreenTimeTheme

/** Main screen. The usage dashboard replaces the status card in a later step. */
@Composable
fun HomeScreen(
    status: HomeStatus,
    onTrackingChange: (Boolean) -> Unit,
    onAllowUsageAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            HomeStatus.Ready -> StatusCard(
                title = stringResource(R.string.home_ready_title),
                body = stringResource(R.string.home_ready_body),
            )
        }
        TrackingSwitchCard(
            checked = status != HomeStatus.TrackingOff,
            onCheckedChange = onTrackingChange,
        )
        FootnoteText(stringResource(R.string.home_privacy_note))
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
private fun TrackingSwitchCard(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SettingsCard {
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
}

@Preview
@Composable
private fun HomeScreenPreview() {
    OpenScreenTimeTheme(dynamicColor = false) {
        HomeScreen(status = HomeStatus.Ready, onTrackingChange = {}, onAllowUsageAccess = {})
    }
}
