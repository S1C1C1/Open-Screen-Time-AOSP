package org.openscreentime.ui.appdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.openscreentime.R
import org.openscreentime.ui.components.AppIcon
import org.openscreentime.ui.components.FootnoteText
import org.openscreentime.ui.components.SettingsCard
import org.openscreentime.ui.components.SettingsScaffold
import org.openscreentime.ui.components.WeekBarChart
import org.openscreentime.ui.dashboard.AppDetail
import org.openscreentime.ui.format.formatDuration

/** One app's usage this week and its limit. */
@Composable
fun AppDetailScreen(
    detail: AppDetail?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    SettingsScaffold(title = detail?.label.orEmpty(), onBack = onBack, modifier = modifier) {
        if (detail == null) return@SettingsScaffold

        SettingsCard {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AppIcon(detail.packageName, detail.label, size = 48.dp)
                    Column {
                        Text(
                            text = stringResource(R.string.dashboard_today),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatDuration(detail.today, resources),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                }
                Text(
                    text = stringResource(
                        R.string.app_detail_daily_average,
                        formatDuration(detail.dailyAverage, resources),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                WeekBarChart(bars = detail.week, selectedDay = detail.week.first { it.isToday }.date)
            }
        }
        SettingsCard {
            // Becomes tappable when app limits are added.
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_limit_title)) },
                supportingContent = { Text(stringResource(R.string.app_limit_none)) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
            )
        }
        FootnoteText(stringResource(R.string.app_limit_coming))
    }
}
