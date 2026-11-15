package dev.dache.app.boards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dache.app.navigation.LocalAppNavigator
import dev.dache.app.resources.Res
import dev.dache.app.resources.chart_col_day
import dev.dache.app.resources.widget_chart_description
import dev.dache.app.resources.widget_edit
import dev.dache.app.resources.widget_expires
import dev.dache.app.resources.widget_more_items
import dev.dache.app.resources.widget_move_earlier
import dev.dache.app.resources.widget_move_later
import dev.dache.app.resources.widget_narrower
import dev.dache.app.resources.widget_no_data
import dev.dache.app.resources.widget_no_items
import dev.dache.app.resources.widget_range_days
import dev.dache.app.resources.widget_remove
import dev.dache.app.resources.widget_stat_description
import dev.dache.app.resources.widget_uptime
import dev.dache.app.resources.widget_wider
import dev.dache.app.ui.chart.ChartCard
import dev.dache.app.ui.chart.ChartSeries
import dev.dache.app.ui.chart.ChartTable
import dev.dache.app.ui.chart.LineChart
import dev.dache.app.ui.chart.StackedColumnChart
import dev.dache.app.ui.components.StatusChip
import dev.dache.app.ui.components.formatDay
import dev.dache.app.ui.components.rememberValueFormats
import dev.dache.app.ui.theme.LocalChartColors
import dev.dache.core.boards.DatapointShape
import dev.dache.core.boards.ItemDetail
import dev.dache.core.boards.ItemTone
import dev.dache.core.boards.Widget
import dev.dache.core.boards.WidgetChart
import dev.dache.core.boards.WidgetItem
import dev.dache.core.boards.WidgetValue
import org.jetbrains.compose.resources.stringResource

/** What edit mode lets the user do to a widget; null callbacks are left out (e.g. moving the first one earlier). */
class WidgetEditActions(
    val onMoveEarlier: (() -> Unit)?,
    val onMoveLater: (() -> Unit)?,
    val onNarrower: (() -> Unit)?,
    val onWider: (() -> Unit)?,
    val onEdit: () -> Unit,
    val onRemove: () -> Unit,
)

/**
 * One widget drawn from live data, the same in the dashboard, in edit mode and in the editor's preview. In edit
 * mode [edit] adds a strip of controls above it; [onLongPress] is how a touch user gets into edit mode.
 */
@Composable
fun WidgetCard(
    widget: Widget,
    value: WidgetValue,
    modifier: Modifier = Modifier,
    edit: WidgetEditActions? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val title = widget.displayTitle()
    Column(
        modifier.then(if (onLongPress != null) Modifier.onLongPress(onLongPress) else Modifier),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        edit?.let { EditStrip(title, widget, it) }
        when (value) {
            is WidgetValue.Series -> SeriesWidget(title, widget, value)
            is WidgetValue.Stat -> StatWidget(title, widget, value)
            is WidgetValue.Items -> ItemsWidget(title, value)
        }
    }
}

@Composable
private fun EditStrip(
    title: String,
    widget: Widget,
    actions: WidgetEditActions,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StripButton(actions.onMoveEarlier, stringResource(Res.string.widget_move_earlier, title)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
        }
        StripButton(actions.onMoveLater, stringResource(Res.string.widget_move_later, title)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
        StripButton(actions.onNarrower, stringResource(Res.string.widget_narrower, title)) {
            Text("−", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "${widget.span}/${Widget.MAX_SPAN}",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.clearAndSetSemantics {},
        )
        StripButton(actions.onWider, stringResource(Res.string.widget_wider, title)) {
            Text("+", style = MaterialTheme.typography.titleMedium)
        }
        StripButton(actions.onEdit, stringResource(Res.string.widget_edit, title)) {
            Icon(Icons.Filled.Edit, contentDescription = null)
        }
        StripButton(actions.onRemove, stringResource(Res.string.widget_remove, title)) {
            Icon(Icons.Filled.Delete, contentDescription = null)
        }
    }
}

/** A small icon button for the edit strip; disabled when there is no [onClick], e.g. at the end of the row. */
@Composable
private fun StripButton(
    onClick: (() -> Unit)?,
    description: String,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = Modifier.size(STRIP_BUTTON).semantics { contentDescription = description },
        content = content,
    )
}

@Composable
private fun SeriesWidget(
    title: String,
    widget: Widget,
    value: WidgetValue.Series,
) {
    val colors = LocalChartColors.current
    val formats = rememberValueFormats()
    val format = { v: Double -> formats.format(v, value.unit, value.currency) }
    val labels = value.points.map { formatDay(it.at) }
    val values = value.points.map { it.value }
    ChartCard(
        title = title,
        subtitle = stringResource(Res.string.widget_range_days, widget.days),
        table =
            ChartTable(
                columns = listOf(stringResource(Res.string.chart_col_day), title),
                rows = value.points.map { listOf(formatDay(it.at), format(it.value)) }.reversed(),
            ),
    ) {
        val enough = if (widget.chart == WidgetChart.LINE) values.size >= 2 else values.any { it != 0.0 }
        if (!enough) {
            Text(stringResource(Res.string.widget_no_data), style = MaterialTheme.typography.bodySmall)
            return@ChartCard
        }
        val description =
            stringResource(
                Res.string.widget_chart_description,
                title,
                widget.days,
                format(values.last()),
                format(values.max()),
            )
        if (widget.chart == WidgetChart.LINE) {
            LineChart(labels, values, title, colors.accent, format, description, plotHeight = PLOT_HEIGHT)
        } else {
            StackedColumnChart(
                labels,
                listOf(ChartSeries(title, colors.accent, values)),
                format,
                description,
                plotHeight = PLOT_HEIGHT,
            )
        }
    }
}

@Composable
private fun StatWidget(
    title: String,
    widget: Widget,
    value: WidgetValue.Stat,
) {
    val formats = rememberValueFormats()
    val number = value.value?.let { formats.format(it, value.unit, value.currency) } ?: "–"
    val description = stringResource(Res.string.widget_stat_description, title, number)
    val subtitle =
        if (widget.datapoint.shape == DatapointShape.SERIES) {
            "${stringResource(widget.effectiveAggregation.label)} · " +
                stringResource(Res.string.widget_range_days, widget.days)
        } else {
            null
        }
    WidgetSurface {
        Column(
            Modifier.clearAndSetSemantics { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            WidgetTitle(title)
            Text(number, style = MaterialTheme.typography.headlineMedium, color = LocalChartColors.current.primaryText)
            // Always a line, even empty, so stat cards side by side are the same height.
            Text(
                subtitle.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = LocalChartColors.current.secondaryText,
                minLines = 1,
            )
        }
    }
}

@Composable
private fun ItemsWidget(
    title: String,
    value: WidgetValue.Items,
) {
    val navigator = LocalAppNavigator.current
    WidgetSurface {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            WidgetTitle(title)
            if (value.items.isEmpty()) {
                Text(stringResource(Res.string.widget_no_items), style = MaterialTheme.typography.bodySmall)
            }
            value.items.take(MAX_ITEMS).forEach { item ->
                ItemRow(item, onClick = { navigator.openResource(item.ref) })
            }
            if (value.items.size > MAX_ITEMS) {
                Text(
                    stringResource(Res.string.widget_more_items, value.items.size - MAX_ITEMS),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalChartColors.current.secondaryText,
                )
            }
        }
    }
}

@Composable
private fun ItemRow(
    item: WidgetItem,
    onClick: () -> Unit,
) {
    val colors = LocalChartColors.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            item.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (item.tone == ItemTone.CRITICAL) colors.critical else colors.primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        when (val detail = item.detail) {
            is ItemDetail.Deploy -> StatusChip(detail.status)
            is ItemDetail.Server -> StatusChip(detail.status)
            is ItemDetail.Monitor -> {
                detail.uptimePercent?.let {
                    Text(
                        stringResource(Res.string.widget_uptime, rememberValueFormats().percent(it)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText,
                    )
                }
                StatusChip(detail.status)
            }
            is ItemDetail.Expires ->
                Text(
                    stringResource(Res.string.widget_expires, formatDay(detail.at)),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.tone == ItemTone.GOOD) colors.secondaryText else colors.critical,
                )
            null -> Unit
        }
    }
}

@Composable
private fun WidgetSurface(content: @Composable () -> Unit) {
    val colors = LocalChartColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun WidgetTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = LocalChartColors.current.primaryText,
        modifier = Modifier.semantics { heading() },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Calls [action] when a pointer is held still for the long-press timeout. It watches in the initial pass and
 * consumes nothing, so the charts inside keep their own hover and tap handling.
 */
private fun Modifier.onLongPress(action: () -> Unit): Modifier =
    pointerInput(action) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val released =
                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    var lifted = false
                    while (!lifted) {
                        val change =
                            awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                        lifted = change == null ||
                            !change.pressed ||
                            (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                    }
                    true
                }
            if (released == null) action()
        }
    }

private val PLOT_HEIGHT = 120.dp
private val STRIP_BUTTON = 36.dp
private const val MAX_ITEMS = 8
