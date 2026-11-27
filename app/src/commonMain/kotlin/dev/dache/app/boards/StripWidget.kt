package dev.dache.app.boards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.chart_uptime_day
import dev.dache.app.resources.widget_more_items
import dev.dache.app.resources.widget_no_data
import dev.dache.app.resources.widget_range_days
import dev.dache.app.resources.widget_strip_description
import dev.dache.app.ui.chart.UptimeDay
import dev.dache.app.ui.chart.UptimeStrip
import dev.dache.app.ui.components.formatDay
import dev.dache.app.ui.components.rememberValueFormats
import dev.dache.app.ui.theme.LocalChartColors
import dev.dache.core.boards.UptimeRow
import dev.dache.core.boards.Widget
import dev.dache.core.boards.WidgetValue
import org.jetbrains.compose.resources.stringResource

/** One uptime strip per monitor; hovering or tapping a day shows its value in place of the row's average. */
@Composable
internal fun StripsWidget(
    title: String,
    widget: Widget,
    value: WidgetValue.Strips,
) {
    val colors = LocalChartColors.current
    val formats = rememberValueFormats()
    val dayTemplate = stringResource(Res.string.chart_uptime_day)
    // Which row and day are pinned by a tap; one at a time across the widget.
    var selected by remember(value) { mutableStateOf<Pair<Int, Int>?>(null) }
    WidgetSurface {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetTitle(title)
            Text(
                stringResource(Res.string.widget_range_days, widget.days),
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryText,
            )
            if (value.rows.isEmpty()) {
                Text(stringResource(Res.string.widget_no_data), style = MaterialTheme.typography.bodySmall)
            }
            value.rows.take(MAX_ITEMS).forEachIndexed { index, row ->
                StripRow(
                    row = row,
                    days = widget.days,
                    dayFormatter = {
                        dayTemplate.replace("%1\$s", it.label).replace("%2\$s", formats.percent(it.percent))
                    },
                    selected = selected?.takeIf { it.first == index }?.second,
                    onSelect = { day -> selected = day?.let { index to it } },
                )
            }
            if (value.rows.size > MAX_ITEMS) {
                Text(
                    stringResource(Res.string.widget_more_items, value.rows.size - MAX_ITEMS),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText,
                )
            }
        }
    }
}

@Composable
private fun StripRow(
    row: UptimeRow,
    days: Int,
    dayFormatter: (UptimeDay) -> String,
    selected: Int?,
    onSelect: (Int?) -> Unit,
) {
    val formats = rememberValueFormats()
    val average = formats.percent(row.average)
    UptimeStrip(
        name = row.name,
        summary = average,
        days = row.days.map { UptimeDay(formatDay(it.at), it.value) },
        dayFormatter = dayFormatter,
        contentDescription =
            stringResource(Res.string.widget_strip_description, row.name, average, row.daysWithDowntime, days),
        selected = selected,
        onSelect = onSelect,
    )
}
