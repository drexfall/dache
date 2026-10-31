package dev.dache.app.ui.chart

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.chart_show_chart
import dev.dache.app.resources.chart_show_table
import dev.dache.app.ui.components.ExpandableBody
import dev.dache.app.ui.components.ExpandableHeader
import dev.dache.app.ui.components.Paged
import dev.dache.app.ui.theme.LocalChartColors
import org.jetbrains.compose.resources.stringResource

enum class LegendShape { SWATCH, LINE }

data class LegendItem(
    val label: String,
    val color: Color,
    val shape: LegendShape = LegendShape.SWATCH,
)

/** The table twin every chart carries, so no value is reachable only by hovering or by color. */
data class ChartTable(
    val columns: List<String>,
    val rows: List<List<String>>,
)

/**
 * Card frame for one chart: a collapsible title, a chart/table toggle, the legend (2+ series only), the plot,
 * then [detail] for whatever the user pinned by tapping or clicking a mark.
 */
@Composable
fun ChartCard(
    title: String,
    table: ChartTable,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    legend: List<LegendItem> = emptyList(),
    detail: (@Composable () -> Unit)? = null,
    chart: @Composable () -> Unit,
) {
    val colors = LocalChartColors.current
    var showTable by rememberSaveable(title) { mutableStateOf(false) }
    var expanded by rememberSaveable(title) { mutableStateOf(true) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExpandableHeader(
                    expanded = expanded,
                    onToggle = { expanded = !expanded },
                    modifier = Modifier.weight(1f),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.primaryText,
                            modifier = Modifier.semantics { heading() },
                        )
                        subtitle?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                        }
                    }
                }
                if (expanded) {
                    TextButton(onClick = { showTable = !showTable }) {
                        Text(
                            stringResource(if (showTable) Res.string.chart_show_chart else Res.string.chart_show_table),
                        )
                    }
                }
            }
            ExpandableBody(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (legend.size >= 2 && !showTable) Legend(legend)
                    if (showTable) DataTable(table) else chart()
                    if (!showTable && detail != null) {
                        HorizontalDivider(color = colors.grid)
                        detail()
                    }
                }
            }
        }
    }
}

@Composable
fun Legend(
    items: List<LegendItem>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalChartColors.current
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val keyModifier =
                    when (item.shape) {
                        LegendShape.SWATCH -> Modifier.size(10.dp)
                        LegendShape.LINE -> Modifier.size(width = 14.dp, height = 2.dp)
                    }
                Box(keyModifier.background(item.color, RoundedCornerShape(2.dp)))
                Text(item.label, style = MaterialTheme.typography.labelMedium, color = colors.secondaryText)
            }
        }
    }
}

@Composable
private fun DataTable(table: ChartTable) {
    val colors = LocalChartColors.current
    Column {
        TableRow(table.columns, colors.mutedText, header = true)
        HorizontalDivider(color = colors.grid)
        Paged(
            table.rows,
            key = "table:${table.columns.joinToString()}",
        ) { TableRow(it, colors.primaryText, header = false) }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    color: Color,
    header: Boolean,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        cells.forEachIndexed { index, cell ->
            Text(
                cell,
                modifier = Modifier.weight(if (index == 0) LABEL_COLUMN_WEIGHT else 1f),
                style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                color = color,
                // Numbers line up on the right; the label column stays left.
                textAlign = if (index == 0) TextAlign.Start else TextAlign.End,
                maxLines = 1,
            )
        }
    }
}

/** The label column gets more room than each value column. */
private const val LABEL_COLUMN_WEIGHT = 1.6f
