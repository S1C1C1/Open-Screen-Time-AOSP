package org.openscreentime.ui.allowed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openscreentime.R
import org.openscreentime.allowed.AllowedApp
import org.openscreentime.allowed.AllowedRole
import org.openscreentime.ui.components.AppIcon
import org.openscreentime.ui.components.FootnoteText
import org.openscreentime.ui.components.SettingsCard
import org.openscreentime.ui.components.SettingsScaffold

/** An always allowed app with the name shown to the user. */
data class AllowedAppUi(val app: AllowedApp, val label: String)

/** Apps that limits and downtime never block. */
@Composable
fun AlwaysAllowedScreen(
    apps: List<AllowedAppUi>,
    pinSet: Boolean,
    onRemove: (AllowedApp) -> Unit,
    onAddClick: () -> Unit,
    onBack: () -> Unit,
    onSetPinClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(title = stringResource(R.string.allowed_title), onBack = onBack, modifier = modifier) {
        Text(
            stringResource(R.string.allowed_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (!pinSet) {
            SettingsCard {
                Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lock),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column {
                        Text(stringResource(R.string.allowed_pin_reminder), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = onSetPinClick) { Text(stringResource(R.string.limits_set_pin)) }
                    }
                }
            }
        }
        SettingsCard {
            apps.forEach { item ->
                AllowedRow(item, onRemove)
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
                headlineContent = { Text(stringResource(R.string.allowed_add), color = MaterialTheme.colorScheme.primary) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
            )
        }
        FootnoteText(stringResource(R.string.allowed_footnote))
    }
}

@Composable
private fun AllowedRow(item: AllowedAppUi, onRemove: (AllowedApp) -> Unit) {
    val role = when (item.app.role) {
        AllowedRole.Phone -> stringResource(R.string.allowed_role_phone)
        AllowedRole.Emergency -> stringResource(R.string.allowed_role_emergency)
        AllowedRole.Settings -> stringResource(R.string.allowed_role_settings)
        AllowedRole.Added -> null
    }
    val label = if (item.app.role == AllowedRole.Emergency) stringResource(R.string.allowed_emergency_label) else item.label
    ListItem(
        leadingContent = { AppIcon(item.app.packageName, label) },
        headlineContent = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = role?.let { { Text(it) } },
        trailingContent = {
            if (item.app.removable) {
                IconButton(onClick = { onRemove(item.app) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.allowed_remove, label),
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lock),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        stringResource(R.string.allowed_cannot_remove),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
    )
}
