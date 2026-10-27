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
import dev.dache.app.resources.Res
import dev.dache.app.resources.chart_col_median
import dev.dache.app.resources.chart_col_runs
import dev.dache.app.resources.chart_hint_bars
import dev.dache.app.resources.detail_recent_builds
import dev.dache.app.ui.chart.BarItem
import dev.dache.app.ui.chart.ChartCard
import dev.dache.app.ui.chart.ChartTable
import dev.dache.app.ui.chart.HorizontalBarChart
import dev.dache.app.ui.components.formatDay
import dev.dache.app.ui.components.formatHour
import dev.dache.app.ui.components.rememberValueFormats
import dev.dache.app.ui.theme.LocalChartColors
import dev.dache.core.dashboard.StatusEvent
import dev.dache.core.dashboard.median
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** One bar's worth of builds (a site's deploys or a workflow's runs) with their durations in seconds. */
data class BuildGroup(
    val label: String,
    val builds: List<Pair<StatusEvent, Int>>,
) {
    val medianSeconds: Int get() = median(builds.map { it.second }) ?: 0
}

/** Median duration per site or workflow; selecting a bar lists its most recent builds. */
@Composable
fun DurationBarsCard(
    title: StringResource,
    subtitle: StringResource,
    groups: List<BuildGroup>,
) {
    val formats = rememberValueFormats()
    val sorted = groups.sortedByDescending { it.medianSeconds }
    var selected by rememberSaveable(title.key) { mutableStateOf<Int?>(null) }
    ChartCard(
        title = stringResource(title),
        subtitle = stringResource(subtitle),
        table =
            ChartTable(
                columns =
                    listOf(
                        "",
                        stringResource(Res.string.chart_col_median),
                        stringResource(Res.string.chart_col_runs),
                    ),
                rows =
                    sorted.map {
                        listOf(
                            it.label,
                            formats.duration(it.medianSeconds.toDouble()),
                            it.builds.size.toString(),
                        )
                    },
            ),
        detail = {
            val group = selected?.let { sorted.getOrNull(it) }
            if (group == null) {
                ChartHint(stringResource(Res.string.chart_hint_bars))
            } else {
                val durations = group.builds.associate { it.first to it.second }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(Res.string.detail_recent_builds, group.label),
                        style = MaterialTheme.typography.titleSmall,
                        color = LocalChartColors.current.primaryText,
                    )
                    EventList(
                        events = group.builds.map { it.first }.sortedByDescending { it.at },
                        whenText = { event ->
                            listOfNotNull(
                                "${formatDay(event.at)} ${formatHour(event.at)}",
                                durations[event]?.let { formats.duration(it.toDouble()) },
                            ).joinToString(" · ")
                        },
                        limit = RECENT_BUILDS,
                    )
                }
            }
        },
    ) {
        HorizontalBarChart(
            items = sorted.map { BarItem(it.label, it.medianSeconds.toDouble()) },
            color = LocalChartColors.current.accent,
            valueFormatter = formats::duration,
            selected = selected,
            onSelect = { selected = it },
        )
    }
}

private const val RECENT_BUILDS = 5
