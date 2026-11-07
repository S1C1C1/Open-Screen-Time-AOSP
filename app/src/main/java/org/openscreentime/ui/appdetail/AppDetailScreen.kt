package org.openscreentime.ui.appdetail

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.openscreentime.R
import org.openscreentime.blocking.BlockDecision
import org.openscreentime.blocking.BlockReason
import org.openscreentime.ui.components.AppIcon
import org.openscreentime.ui.components.FootnoteText
import org.openscreentime.ui.components.SettingsCard
import org.openscreentime.ui.components.SettingsScaffold
import org.openscreentime.ui.components.WeekBarChart
import org.openscreentime.ui.dashboard.AppDetail
import org.openscreentime.ui.format.formatDuration
import org.openscreentime.ui.format.formatLimit
import org.openscreentime.ui.format.formatTime

/** One app's usage this week and its limit. */
@Composable
fun AppDetailScreen(
    detail: AppDetail?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onLimitClick: () -> Unit = {},
    decision: BlockDecision? = null,
    onPreviewBlock: () -> Unit = {},
    blockingActive: Boolean = false,
    onGetMoreTime: () -> Unit = {},
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
            val limit = detail.limit
            val timeLeft = detail.timeLeftToday
            ListItem(
                modifier = Modifier.clickable(onClick = onLimitClick),
                headlineContent = { Text(stringResource(R.string.app_limit_title)) },
                supportingContent = {
                    Text(if (limit == null) stringResource(R.string.app_limit_none) else formatLimit(limit, resources))
                },
                trailingContent = if (timeLeft != null) {
                    {
                        Text(
                            text = if (timeLeft.isZero) {
                                stringResource(R.string.app_limit_reached)
                            } else {
                                stringResource(R.string.app_limit_time_left, formatDuration(timeLeft, resources))
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (timeLeft.isZero) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    null
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
            )
        }
        if (decision != null) {
            SettingsCard {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(blockStatusText(decision), style = MaterialTheme.typography.bodyMedium)
                    if (blockingActive && decision is BlockDecision.Blocked) {
                        // The app is really paused: this opens the real block screen to get more time.
                        Button(onClick = onGetMoreTime) { Text(stringResource(R.string.block_get_more_time)) }
                    } else {
                        OutlinedButton(onClick = onPreviewBlock) { Text(stringResource(R.string.block_preview)) }
                    }
                }
            }
        }
        if (detail.limit != null) FootnoteText(stringResource(R.string.app_limit_blocking_coming))
    }
}

@Composable
private fun blockStatusText(decision: BlockDecision): String = when (decision) {
    BlockDecision.Allowed -> stringResource(R.string.status_allowed)
    is BlockDecision.AllowedUntil -> stringResource(
        R.string.status_unlocked,
        formatTime(
            decision.until.atZone(java.time.ZoneId.systemDefault()).toLocalTime(),
            LocalConfiguration.current.locales[0],
            DateFormat.is24HourFormat(LocalContext.current),
        ),
    )
    is BlockDecision.Blocked -> when (decision.reason) {
        BlockReason.Downtime -> stringResource(R.string.status_blocked_downtime)
        BlockReason.Scheduled -> stringResource(R.string.status_blocked_schedule, decision.ruleName.orEmpty())
        BlockReason.LimitReached -> stringResource(R.string.status_blocked_limit)
    }
}
