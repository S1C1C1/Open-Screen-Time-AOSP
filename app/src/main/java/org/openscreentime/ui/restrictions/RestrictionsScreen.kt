package org.openscreentime.ui.restrictions

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.openscreentime.R
import org.openscreentime.restrictions.Restriction
import org.openscreentime.ui.components.FootnoteText
import org.openscreentime.ui.components.SettingsCard
import org.openscreentime.ui.components.SettingsScaffold
import org.openscreentime.ui.format.formatTime
import java.time.Instant
import java.time.ZoneId

/** What the Restrictions screen shows. */
data class RestrictionsUi(
    val enabled: Set<Restriction> = emptySet(),
    /** Restrictions lifted with the PIN, and roughly when each lift ends. */
    val liftedUntil: Map<Restriction, Instant> = emptyMap(),
)

val Restriction.titleRes: Int
    get() = when (this) {
        Restriction.Installs -> R.string.restriction_installs
        Restriction.DeveloperOptions -> R.string.restriction_developer
        Restriction.Users -> R.string.restriction_users
        Restriction.DateTime -> R.string.restriction_date_time
    }

private val Restriction.descriptionRes: Int
    get() = when (this) {
        Restriction.Installs -> R.string.restriction_installs_description
        Restriction.DeveloperOptions -> R.string.restriction_developer_description
        Restriction.Users -> R.string.restriction_users_description
        Restriction.DateTime -> R.string.restriction_date_time_description
    }

/**
 * Four switches from the spec. Turning one on is free; turning it off, or
 * allowing it for 15 minutes, asks for the PIN when one is set.
 */
@Composable
fun RestrictionsScreen(
    ui: RestrictionsUi,
    blockingActive: Boolean,
    onToggle: (Restriction, Boolean) -> Unit,
    onAllowFor15Minutes: (Restriction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(title = stringResource(R.string.restrictions_title), onBack = onBack, modifier = modifier) {
        Text(
            stringResource(R.string.restrictions_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (!blockingActive) {
            SettingsCard {
                Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.restrictions_need_device_owner), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        SettingsCard {
            Restriction.entries.forEachIndexed { index, restriction ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                RestrictionRow(
                    restriction = restriction,
                    on = restriction in ui.enabled,
                    liftedUntil = ui.liftedUntil[restriction],
                    onToggle = { onToggle(restriction, it) },
                    onAllow = { onAllowFor15Minutes(restriction) },
                )
            }
        }
        FootnoteText(stringResource(R.string.restrictions_footnote))
    }
}

@Composable
private fun RestrictionRow(
    restriction: Restriction,
    on: Boolean,
    liftedUntil: Instant?,
    onToggle: (Boolean) -> Unit,
    onAllow: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = on, role = Role.Switch, onValueChange = onToggle),
        ) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text(stringResource(restriction.titleRes), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(restriction.descriptionRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = on, onCheckedChange = null)
        }
        if (on) {
            if (liftedUntil != null) {
                Text(
                    stringResource(
                        R.string.restriction_allowed_until,
                        formatTime(
                            liftedUntil.atZone(ZoneId.systemDefault()).toLocalTime(),
                            LocalConfiguration.current.locales[0],
                            DateFormat.is24HourFormat(LocalContext.current),
                        ),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                TextButton(onClick = onAllow, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.restriction_allow_15))
                }
            }
        }
    }
}
