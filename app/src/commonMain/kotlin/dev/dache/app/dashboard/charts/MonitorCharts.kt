package dev.dache.app.dashboard.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.chart_col_day
import dev.dache.app.resources.chart_col_hour
import dev.dache.app.resources.chart_hint_cells
import dev.dache.app.resources.chart_hint_points
import dev.dache.app.resources.chart_no_data
import dev.dache.app.resources.chart_response_description
import dev.dache.app.resources.chart_response_subtitle
import dev.dache.app.resources.chart_response_title
import dev.dache.app.resources.chart_uptime_day
import dev.dache.app.resources.chart_uptime_description
import dev.dache.app.resources.chart_uptime_downtime
import dev.dache.app.resources.chart_uptime_no_downtime
import dev.dache.app.resources.chart_uptime_subtitle
import dev.dache.app.resources.chart_uptime_title
import dev.dache.app.resources.detail_uptime_day
import dev.dache.app.resources.link_open_monitor
import dev.dache.app.resources.open_in
import dev.dache.app.resources.series_response_time
import dev.dache.app.ui.chart.ChartCard
import dev.dache.app.ui.chart.ChartTable
import dev.dache.app.ui.chart.LegendItem
import dev.dache.app.ui.chart.LineChart
import dev.dache.app.ui.chart.UptimeDay
import dev.dache.app.ui.chart.UptimeStrip
import dev.dache.app.ui.components.ExternalLinkButton
import dev.dache.app.ui.components.ExternalLinkIconButton
import dev.dache.app.ui.components.formatDay
import dev.dache.app.ui.components.formatHour
import dev.dache.app.ui.components.rememberValueFormats
import dev.dache.app.ui.theme.LocalChartColors
import dev.dache.core.model.Monitor
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** One strip per monitor; tapping a day pins it and explains it below. */
@Composable
fun UptimeCard(monitors: List<Monitor>) {
    val colors = LocalChartColors.current
    val formats = rememberValueFormats()
    val withHistory = monitors.filter { it.dailyUptime.isNotEmpty() }
    val days = withHistory.first().dailyUptime.map { formatDay(it.at) }
    val dayTemplate = stringResource(Res.string.chart_uptime_day)
    // (monitor index, day index) of the pinned cell.
    var selectedMonitor by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedDay by rememberSaveable { mutableStateOf<Int?>(null) }

    ChartCard(
        title = stringResource(Res.string.chart_uptime_title),
        subtitle = stringResource(Res.string.chart_uptime_subtitle),
        legend =
            listOf(
                LegendItem(stringResource(Res.string.chart_uptime_downtime), colors.critical),
                LegendItem(stringResource(Res.string.chart_uptime_no_downtime), colors.quiet),
            ),
        table =
            ChartTable(
                columns = listOf(stringResource(Res.string.chart_col_day)) + withHistory.map { it.name },
                rows =
                    days.indices
                        .map { i ->
                            listOf(days[i]) +
                                withHistory.map { m ->
                                    m.dailyUptime.getOrNull(i)?.let { formats.percent(it.value) }
                                        ?: "–"
                                }
                        }.reversed(),
            ),
        detail = {
            val monitor = selectedMonitor?.let { withHistory.getOrNull(it) }
            val day = selectedDay?.let { monitor?.dailyUptime?.getOrNull(it) }
            if (monitor == null || day == null) {
                ChartHint(stringResource(Res.string.chart_hint_cells))
            } else {
                val downMinutes = ((FULL_UPTIME - day.value) / FULL_UPTIME * MINUTES_PER_DAY).roundToInt()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(
                            Res.string.detail_uptime_day,
                            monitor.name,
                            formatDay(day.at),
                            formats.percent(day.value),
                            downMinutes,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.primaryText,
                    )
                    ExternalLinkButton(
                        stringResource(Res.string.open_in, monitor.providerId.displayName),
                        monitor.webUrl,
                    )
                }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            withHistory.forEachIndexed { monitorIndex, monitor ->
                val stripDays = monitor.dailyUptime.map { UptimeDay(formatDay(it.at), it.value) }
                val average = monitor.dailyUptime.map { it.value }.average()
                UptimeStrip(
                    name = monitor.name,
                    summary = formats.percent(average),
                    days = stripDays,
                    dayFormatter = {
                        dayTemplate
                            .replace(
                                "%1\$s",
                                it.label,
                            ).replace("%2\$s", formats.percent(it.percent))
                    },
                    contentDescription =
                        stringResource(
                            Res.string.chart_uptime_description,
                            monitor.name,
                            formats.percent(average),
                            stripDays.count { it.percent < FULL_UPTIME },
                        ),
                    selected = selectedDay.takeIf { selectedMonitor == monitorIndex },
                    onSelect = { day ->
                        selectedMonitor = day?.let { monitorIndex }
                        selectedDay = day
                    },
                )
            }
        }
    }
}

/** Small multiples: each monitor gets its own small line chart instead of several series sharing one plot. */
@Composable
fun ResponseTimeCard(monitors: List<Monitor>) {
    val colors = LocalChartColors.current
    val formats = rememberValueFormats()
    val withData = monitors.filter { it.responseTimes.isNotEmpty() }
    val hours = withData.maxBy { it.responseTimes.size }.responseTimes.map { it.at }
    val seriesName = stringResource(Res.string.series_response_time)
    ChartCard(
        title = stringResource(Res.string.chart_response_title),
        subtitle = stringResource(Res.string.chart_response_subtitle),
        table =
            ChartTable(
                columns = listOf(stringResource(Res.string.chart_col_hour)) + withData.map { it.name },
                rows =
                    hours
                        .map { at ->
                            listOf(formatHour(at)) +
                                withData.map { m ->
                                    m.responseTimes.firstOrNull { it.at == at }?.let { formats.milliseconds(it.value) }
                                        ?: "–"
                                }
                        }.reversed(),
            ),
        detail = { ChartHint(stringResource(Res.string.chart_hint_points)) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            withData.forEach { monitor ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            monitor.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.primaryText,
                        )
                        ExternalLinkIconButton(
                            monitor.webUrl,
                            stringResource(Res.string.link_open_monitor, monitor.name),
                        )
                    }
                    if (monitor.responseTimes.size < 2) {
                        Text(stringResource(Res.string.chart_no_data), style = MaterialTheme.typography.bodySmall)
                    } else {
                        LineChart(
                            labels = monitor.responseTimes.map { formatHour(it.at) },
                            values = monitor.responseTimes.map { it.value },
                            seriesName = seriesName,
                            color = colors.accent,
                            valueFormatter = formats::milliseconds,
                            contentDescription =
                                stringResource(
                                    Res.string.chart_response_description,
                                    monitor.name,
                                    formats.milliseconds(monitor.responseTimes.last().value),
                                    formats.milliseconds(monitor.responseTimes.maxOf { it.value }),
                                ),
                            plotHeight = 72.dp,
                        )
                    }
                }
            }
        }
    }
}

private const val FULL_UPTIME = 100.0
private const val MINUTES_PER_DAY = 1_440
