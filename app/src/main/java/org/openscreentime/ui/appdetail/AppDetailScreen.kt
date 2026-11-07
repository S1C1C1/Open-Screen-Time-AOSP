package org.openscreentime.ui.appdetail

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openscreentime.R
import org.openscreentime.blocking.BlockDecision
import org.openscreentime.blocking.BlockReason
import org.openscreentime.ui.components.AppIcon
import org.openscreentime.ui.components.SettingsCard
import org.openscreentime.ui.components.SettingsScaffold
import org.openscreentime.ui.components.WeekBarChart
import org.openscreentime.ui.dashboard.AppDetail
import org.openscreentime.ui.dashboard.GroupOnApp
import org.openscreentime.ui.dashboard.ScheduleOnApp
import org.openscreentime.ui.format.formatDays
import org.openscreentime.ui.format.formatWindow
import java.time.DayOfWeek
import java.time.temporal.WeekFields
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
    onGroupClick: (Long) -> Unit = {},
    onScheduleClick: (Long) -> Unit = {},
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
        if (detail.groups.isNotEmpty()) {
            SectionTitle(stringResource(R.string.app_groups_header))
            SettingsCard {
                detail.groups.forEachIndexed { index, group ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                    GroupRow(group, onClick = { onGroupClick(group.id) })
                }
            }
        }
        if (detail.schedules.isNotEmpty()) {
            SectionTitle(stringResource(R.string.app_schedules_header))
            SettingsCard {
                detail.schedules.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                    ScheduleRow(item, onClick = { onScheduleClick(item.schedule.id) })
                }
            }
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
    }
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

/** A group the app is in: its shared limit and what's left today. */
@Composable
private fun GroupRow(group: GroupOnApp, onClick: () -> Unit) {
    val resources = LocalResources.current
    val limit = group.limit
    val left = group.timeLeftToday
    val summary = when {
        limit == null || left == null -> stringResource(R.string.app_group_no_limit)
        left.isZero -> stringResource(R.string.app_group_limit_reached, formatLimit(limit, resources))
        else -> stringResource(R.string.app_group_limit_left, formatLimit(limit, resources), formatDuration(left, resources))
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(painterResource(R.drawable.ic_apps), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        headlineContent = { Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(summary, color = if (left?.isZero == true) MaterialTheme.colorScheme.error else Color.Unspecified)
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
    )
}

/** A schedule that blocks the app: when it runs, and whether it's on now. */
@Composable
private fun ScheduleRow(item: ScheduleOnApp, onClick: () -> Unit) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val schedule = item.schedule
    val title = if (item.viaGroup != null) stringResource(R.string.app_schedule_via_group, schedule.name, item.viaGroup) else schedule.name
    val activeUntil = item.activeUntil
    val summary = when {
        !schedule.enabled -> stringResource(R.string.app_schedule_off)
        activeUntil != null -> stringResource(R.string.app_schedule_on_now, formatTime(activeUntil.toLocalTime(), locale, use24Hour))
        else -> stringResource(
            R.string.schedules_summary,
            formatDays(schedule.days, firstDayOrder(locale), resources, locale),
            formatWindow(schedule.window, resources, locale, use24Hour),
        )
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(painterResource(R.drawable.ic_schedule), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(summary, color = if (activeUntil != null) MaterialTheme.colorScheme.error else Color.Unspecified)
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
    )
}

/** Days of the week in the order the phone's region uses. */
private fun firstDayOrder(locale: java.util.Locale): List<DayOfWeek> {
    val first = WeekFields.of(locale).firstDayOfWeek
    return (0L until 7L).map { first.plus(it) }
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
        BlockReason.LimitReached -> decision.ruleName?.let { stringResource(R.string.status_blocked_group, it) }
            ?: stringResource(R.string.status_blocked_limit)
    }
}
