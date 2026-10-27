package dev.dache.app.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dache.app.ui.theme.ChartColors
import dev.dache.app.ui.theme.LocalChartColors

/** One stacked series: a value per category, drawn bottom-up in list order. */
data class ChartSeries(
    val name: String,
    val color: Color,
    val values: List<Double>,
)

/**
 * Columns stacked from a shared zero baseline. Columns are at most 24dp wide with a 4dp rounded data end and
 * stacked segments are separated by a 2dp surface gap. Hovering shows a readout; tapping or clicking a
 * column pins it ([selected]) and tapping it again unpins.
 */
@Composable
fun StackedColumnChart(
    labels: List<String>,
    series: List<ChartSeries>,
    valueFormatter: (Double) -> String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    plotHeight: Dp = 160.dp,
    selected: Int? = null,
    onSelect: ((Int?) -> Unit)? = null,
) {
    val colors = LocalChartColors.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 11.sp, color = colors.mutedText)
    val totals = labels.indices.map { i -> series.sumOf { it.values.getOrElse(i) { 0.0 } } }
    val scale = niceScale(totals.maxOrNull() ?: 0.0)
    val tickLabels = remember(scale, colors) { scale.ticks.map { textMeasurer.measure(compactNumber(it), axisStyle) } }
    val labelBand = with(density) { axisStyle.fontSize.toPx() * LABEL_LINE_HEIGHT + 6.dp.toPx() }
    val geometry = ColumnGeometry(density, tickLabels, plotHeight, labels.size)

    var widthPx by remember { mutableStateOf(0f) }
    var hovered by remember { mutableStateOf<Int?>(null) }

    Box(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(plotHeight + with(density) { labelBand.toDp() })
                .onSizeChanged { widthPx = it.width.toFloat() }
                .trackPointerX(
                    onX = { x -> hovered = x?.let { geometry.columnAt(it, widthPx) } },
                    onTap = { x ->
                        geometry.columnAt(x, widthPx)?.let { index ->
                            onSelect?.invoke(index.takeUnless { it == selected })
                        }
                    },
                ).semantics { this.contentDescription = contentDescription },
        ) {
            val plot = geometry.plot(size.width)
            drawFocusBands(plot, geometry.slot(plot), selected, hovered, colors)
            drawValueAxis(plot, scale, tickLabels, colors.grid, colors.baseline, 6.dp.toPx())
            labels.indices.forEach { index ->
                drawStack(index, series, plot, scale, geometry.center(plot, index), geometry.slot(plot))
            }
            drawCategoryLabels(textMeasurer, labels, { geometry.center(plot, it) }, plot, axisStyle, 6.dp.toPx())
        }
        // Hover only: a pinned column is explained by the card's detail panel, so a tooltip would just cover it.
        hovered?.let { index ->
            ChartTooltip(
                anchorX = geometry.center(geometry.plot(widthPx), index),
                title = labels[index],
                rows = series.reversed().map { TooltipRow(it.color, valueFormatter(it.values[index]), it.name) },
            )
        }
    }
}

/** Maps between column indices and pixels; shared by drawing and pointer handling so they always agree. */
private class ColumnGeometry(
    private val density: Density,
    private val tickLabels: List<TextLayoutResult>,
    private val plotHeight: Dp,
    private val count: Int,
) {
    fun plot(width: Float): PlotArea =
        with(density) {
            val left = (tickLabels.maxOfOrNull { it.size.width } ?: 0) + 8.dp.toPx()
            PlotArea(left = left, top = 8.dp.toPx(), right = width, bottom = plotHeight.toPx())
        }

    fun slot(plot: PlotArea): Float = if (count == 0) 0f else plot.width / count

    fun center(
        plot: PlotArea,
        index: Int,
    ): Float = plot.left + slot(plot) * index + slot(plot) / 2f

    fun columnAt(
        x: Float,
        width: Float,
    ): Int? {
        val plot = plot(width)
        return if (count == 0 || x < plot.left) null else ((x - plot.left) / slot(plot)).toInt().coerceIn(0, count - 1)
    }
}

/** A pinned column keeps a solid band; hover adds a lighter one. */
private fun DrawScope.drawFocusBands(
    plot: PlotArea,
    slot: Float,
    selected: Int?,
    hovered: Int?,
    colors: ChartColors,
) {
    selected?.let { index ->
        drawRect(colors.grid, topLeft = Offset(plot.left + slot * index, plot.top), size = Size(slot, plot.height))
    }
    hovered?.takeIf { it != selected }?.let { index ->
        drawRect(
            colors.grid.copy(alpha = HOVER_BAND_ALPHA),
            topLeft = Offset(plot.left + slot * index, plot.top),
            size = Size(slot, plot.height),
        )
    }
}

/**
 * One column: segments bottom-up, a 2dp surface gap carved from the top of every segment that has another above
 * it, and a 4dp rounded data end on the topmost segment only.
 */
private fun DrawScope.drawStack(
    index: Int,
    series: List<ChartSeries>,
    plot: PlotArea,
    scale: AxisScale,
    center: Float,
    slot: Float,
) {
    val barWidth = minOf(24.dp.toPx(), slot * BAR_SLOT_FRACTION)
    val gap = 2.dp.toPx()
    val left = center - barWidth / 2f
    val visible = series.filter { it.values.getOrElse(index) { 0.0 } > 0.0 }
    var stackBottom = plot.bottom
    visible.forEachIndexed { position, segment ->
        val top = stackBottom - (segment.values[index] / scale.max * plot.height).toFloat()
        if (position == visible.lastIndex) {
            drawPath(topRoundedRect(left, top, left + barWidth, stackBottom, 4.dp.toPx()), segment.color)
        } else {
            val segmentTop = minOf(top + gap, stackBottom)
            drawRect(segment.color, topLeft = Offset(left, segmentTop), size = Size(barWidth, stackBottom - segmentTop))
        }
        stackBottom = top
    }
}

/** Bars fill at most this share of their slot; the rest is air between columns. */
private const val BAR_SLOT_FRACTION = 0.6f
private const val HOVER_BAND_ALPHA = 0.6f
