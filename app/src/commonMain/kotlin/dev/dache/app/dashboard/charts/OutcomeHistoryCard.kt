package dev.dache.app.dashboard.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import dev.dache.app.dashboard.HISTORY_DAYS
import dev.dache.app.resources.Res
import dev.dache.app.resources.chart_col_cancelled
import dev.dache.app.resources.chart_col_day
import dev.dache.app.resources.chart_hint_columns
import dev.dache.app.resources.chart_last_14_days
import dev.dache.app.resources.detail_day_summary
import dev.dache.app.resources.detail_nothing_that_day
import dev.dache.app.resources.series_failed
import dev.dache.app.resources.series_succeeded
import dev.dache.app.ui.chart.ChartCard
import dev.dache.app.ui.chart.ChartSeries
import dev.dache.app.ui.chart.ChartTable
import dev.dache.app.ui.chart.LegendItem
import dev.dache.app.ui.chart.StackedColumnChart
import dev.dache.app.ui.components.formatDay
import dev.dache.app.ui.components.formatHour
import dev.dache.app.ui.theme.LocalChartColors
import dev.dache.core.dashboard.StatusEvent
import dev.dache.core.dashboard.dailyStatusCounts
import dev.dache.core.dashboard.onUtcDay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Builds per day. Emphasis form: failures in critical red on top of everything else in gray. Tapping a column
 * pins it and lists that day's builds below the chart.
 */
@Composable
fun OutcomeHistoryCard(
    title: StringResource,
    description: StringResource,
    events: List<StatusEvent>,
    now: Instant,
) {
    val colors = LocalChartColors.current
    val counts = dailyStatusCounts(events, HISTORY_DAYS, now)
    val labels = counts.map { formatDay(it.day) }
    val succeeded = stringResource(Res.string.series_succeeded)
    val failed = stringResource(Res.string.series_failed)
    var selected by rememberSaveable(title.key) { mutableStateOf<Int?>(null) }

    ChartCard(
        title = stringResource(title),
        subtitle = stringResource(Res.string.chart_last_14_days),
        legend = listOf(LegendItem(failed, colors.critical), LegendItem(succeeded, colors.deemphasis)),
        table =
            ChartTable(
                columns =
                    listOf(
                        stringResource(Res.string.chart_col_day),
                        succeeded,
                        failed,
                        stringResource(Res.string.chart_col_cancelled),
                    ),
                rows =
                    counts
                        .mapIndexed { i, day ->
                            listOf(labels[i], day.succeeded.toString(), day.failed.toString(), day.cancelled.toString())
                        }.reversed(),
            ),
        detail = {
            val index = selected
            if (index == null) {
                ChartHint(stringResource(Res.string.chart_hint_columns))
            } else {
                val dayEvents = events.onUtcDay(counts[index].day)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            Res.string.detail_day_summary,
                            labels[index],
                            counts[index].total,
                            counts[index].failed,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.primaryText,
                    )
                    if (dayEvents.isEmpty()) {
                        ChartHint(stringResource(Res.string.detail_nothing_that_day))
                    } else {
                        EventList(dayEvents, whenText = { formatHour(it.at) })
                    }
                }
            }
        },
    ) {
        StackedColumnChart(
            labels = labels,
            series =
                listOf(
                    ChartSeries(succeeded, colors.deemphasis, counts.map { it.succeeded.toDouble() }),
                    ChartSeries(failed, colors.critical, counts.map { it.failed.toDouble() }),
                ),
            valueFormatter = { it.toInt().toString() },
            contentDescription = stringResource(description, counts.sumOf { it.total }, counts.sumOf { it.failed }),
            selected = selected,
            onSelect = { selected = it },
        )
    }
}
