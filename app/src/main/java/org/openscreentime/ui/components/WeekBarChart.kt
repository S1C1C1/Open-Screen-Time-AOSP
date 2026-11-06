package org.openscreentime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openscreentime.R
import org.openscreentime.ui.dashboard.DayBar
import org.openscreentime.ui.format.formatDuration
import java.time.Duration
import java.time.LocalDate
import java.time.format.TextStyle

/**
 * A bar per day of the week. The [selectedDay] is drawn in the strong
 * color. If [onDayClick] is set, tapping a past day or today selects it.
 */
@Composable
fun WeekBarChart(
    bars: List<DayBar>,
    selectedDay: LocalDate?,
    modifier: Modifier = Modifier,
    onDayClick: ((LocalDate) -> Unit)? = null,
) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val scaleMax = chartScaleMax(bars.maxOfOrNull { it.time } ?: Duration.ZERO)

    Column(modifier = modifier.fillMaxWidth()) {
        // Scale: the top of the chart, e.g. "4 hr".
        Text(
            text = formatDuration(scaleMax, resources),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.End),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_AREA_HEIGHT),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            bars.forEach { bar ->
                val dayName = bar.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
                val onClick = onDayClick?.takeIf { !bar.isFuture }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(
                            if (onClick != null) {
                                Modifier.clickable(role = Role.Tab) { onClick(bar.date) }
                            } else {
                                Modifier
                            },
                        )
                        .semantics(mergeDescendants = true) {
                            contentDescription = if (bar.isFuture) {
                                dayName
                            } else {
                                resources.getString(
                                    R.string.chart_bar_description,
                                    dayName,
                                    formatDuration(bar.time, resources),
                                )
                            }
                            selected = bar.date == selectedDay
                        },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (!bar.isFuture && !bar.time.isZero) {
                        val fraction = (bar.time.toMillis().toFloat() / scaleMax.toMillis()).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                // A sliver of a bar stays visible even for a few seconds of use.
                                .fillMaxHeight(fraction.coerceAtLeast(MIN_BAR_FRACTION))
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(
                                    if (bar.date == selectedDay) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    },
                                ),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            bars.forEach { bar ->
                Text(
                    text = bar.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (bar.isToday) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        bar.isToday -> MaterialTheme.colorScheme.primary
                        bar.isFuture -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Top of the chart: the longest day rounded up to a whole hour, at least 1 hour. */
fun chartScaleMax(longestDay: Duration): Duration {
    val hours = (longestDay.toMinutes() + 59) / 60
    return Duration.ofHours(hours.coerceAtLeast(1))
}

private val BAR_AREA_HEIGHT = 140.dp
private const val MIN_BAR_FRACTION = 0.02f
