package dev.dache.app.dashboard.charts

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.dache.app.resources.Res
import dev.dache.app.resources.chart_col_cost
import dev.dache.app.resources.chart_col_day
import dev.dache.app.resources.chart_cost_description
import dev.dache.app.resources.chart_cost_subtitle
import dev.dache.app.resources.chart_cost_title
import dev.dache.app.resources.chart_hint_columns_cost
import dev.dache.app.resources.detail_cost_day
import dev.dache.app.resources.series_cost
import dev.dache.app.ui.chart.ChartCard
import dev.dache.app.ui.chart.ChartSeries
import dev.dache.app.ui.chart.ChartTable
import dev.dache.app.ui.chart.StackedColumnChart
import dev.dache.app.ui.components.formatDay
import dev.dache.app.ui.components.rememberValueFormats
import dev.dache.app.ui.theme.LocalChartColors
import dev.dache.core.model.Billing
import org.jetbrains.compose.resources.stringResource

/** Daily spend as single-series columns; tapping a day shows its share of the period. */
@Composable
fun CostCard(billing: Billing) {
    val formats = rememberValueFormats()
    val labels = billing.dailyCost.map { formatDay(it.at) }
    val money = { value: Double -> formats.money(value, billing.currency) }
    val total = billing.dailyCost.sumOf { it.value }
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    ChartCard(
        title = stringResource(Res.string.chart_cost_title),
        subtitle = stringResource(Res.string.chart_cost_subtitle, money(billing.monthToDateUsage)),
        table =
            ChartTable(
                columns = listOf(stringResource(Res.string.chart_col_day), stringResource(Res.string.chart_col_cost)),
                rows = billing.dailyCost.mapIndexed { i, point -> listOf(labels[i], money(point.value)) }.reversed(),
            ),
        detail = {
            val point = selected?.let { billing.dailyCost.getOrNull(it) }
            if (point == null) {
                ChartHint(stringResource(Res.string.chart_hint_columns_cost))
            } else {
                val share = if (total > 0) point.value / total * PERCENT else 0.0
                Text(
                    stringResource(
                        Res.string.detail_cost_day,
                        formatDay(point.at),
                        money(point.value),
                        formats.percent(share, 0),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalChartColors.current.primaryText,
                )
            }
        },
    ) {
        StackedColumnChart(
            labels = labels,
            series =
                listOf(
                    ChartSeries(
                        stringResource(
                            Res.string.series_cost,
                        ),
                        LocalChartColors.current.accent,
                        billing.dailyCost.map {
                            it.value
                        },
                    ),
                ),
            valueFormatter = money,
            contentDescription = stringResource(Res.string.chart_cost_description, money(total)),
            selected = selected,
            onSelect = { selected = it },
        )
    }
}

private const val PERCENT = 100.0
