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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dache.app.ui.theme.LocalChartColors
import kotlin.math.roundToInt

/**
 * Single-series line: 2dp stroke, a 10% area wash, the last value labelled at the end dot, and a crosshair
 * that snaps to the nearest point on hover or touch.
 */
@Composable
fun LineChart(
    labels: List<String>,
    values: List<Double>,
    seriesName: String,
    color: Color,
    valueFormatter: (Double) -> String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    plotHeight: Dp = 120.dp,
) {
    val colors = LocalChartColors.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 11.sp, color = colors.mutedText)
    val endLabelStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.primaryText)
    val scale = niceScale(values.maxOrNull() ?: 0.0, targetTicks = 3)
    val tickLabels = remember(scale, colors) { scale.ticks.map { textMeasurer.measure(compactNumber(it), axisStyle) } }
    val labelBand = with(density) { axisStyle.fontSize.toPx() * LABEL_LINE_HEIGHT + 6.dp.toPx() }
    val geometry = LineGeometry(density, tickLabels, plotHeight, values.size)

    var widthPx by remember { mutableStateOf(0f) }
    var hovered by remember { mutableStateOf<Int?>(null) }

    // Tap or click pins a point so its readout stays after the pointer leaves; tapping it again unpins.
    var pinned by remember { mutableStateOf<Int?>(null) }
    val focus = hovered ?: pinned

    Box(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(plotHeight + with(density) { labelBand.toDp() })
                .onSizeChanged { widthPx = it.width.toFloat() }
                .trackPointerX(
                    onX = { x -> hovered = x?.let { geometry.nearestIndex(it, widthPx) } },
                    onTap = { x -> geometry.nearestIndex(x, widthPx)?.let { pinned = it.takeUnless { it == pinned } } },
                ).semantics { this.contentDescription = contentDescription },
        ) {
            val plot = geometry.plot(size.width)
            drawValueAxis(plot, scale, tickLabels, colors.grid, colors.baseline, 6.dp.toPx())
            if (values.isEmpty()) return@Canvas
            val points = values.mapIndexed { i, v -> Offset(geometry.x(plot, i), plot.y(v, scale)) }
            drawSeries(points, plot, color)
            focus?.let { index ->
                drawLine(colors.mutedText, Offset(points[index].x, plot.top), Offset(points[index].x, plot.bottom), 1f)
                drawRingedDot(points[index], color, colors.surface)
            }
            drawRingedDot(points.last(), color, colors.surface)
            // The end label is the one direct label; it gives way to the tooltip while hovering.
            if (focus == null) {
                val label = textMeasurer.measure(valueFormatter(values.last()), endLabelStyle)
                val x = (points.last().x - label.size.width).coerceAtLeast(plot.left)
                val y = (points.last().y - label.size.height - 6.dp.toPx()).coerceAtLeast(0f)
                drawText(label, topLeft = Offset(x, y))
            }
            drawCategoryLabels(textMeasurer, labels, { geometry.x(plot, it) }, plot, axisStyle, 6.dp.toPx())
        }
        focus?.let { index ->
            ChartTooltip(
                anchorX = geometry.x(geometry.plot(widthPx), index),
                title = labels.getOrElse(index) { "" },
                rows = listOf(TooltipRow(color, valueFormatter(values[index]), seriesName)),
            )
        }
    }
}

/** Maps between point indices and pixels; shared by drawing and pointer handling so they always agree. */
private class LineGeometry(
    private val density: Density,
    private val tickLabels: List<TextLayoutResult>,
    private val plotHeight: Dp,
    private val count: Int,
) {
    fun plot(width: Float): PlotArea =
        with(density) {
            val left = (tickLabels.maxOfOrNull { it.size.width } ?: 0) + 8.dp.toPx()
            // Right padding leaves room for the end dot's ring.
            PlotArea(left = left, top = 8.dp.toPx(), right = width - 8.dp.toPx(), bottom = plotHeight.toPx())
        }

    fun x(
        plot: PlotArea,
        index: Int,
    ): Float = if (count <= 1) plot.left + plot.width / 2f else plot.left + plot.width * index / (count - 1)

    fun nearestIndex(
        x: Float,
        width: Float,
    ): Int? {
        val plot = plot(width)
        return when {
            count == 0 || x < plot.left - HIT_SLOP_PX -> null
            count == 1 -> 0
            else -> ((x - plot.left) / plot.width * (count - 1)).roundToInt().coerceIn(0, count - 1)
        }
    }
}

/** 2dp line with round joins over a 10% wash of the same hue, both anchored to the baseline. */
private fun DrawScope.drawSeries(
    points: List<Offset>,
    plot: PlotArea,
    color: Color,
) {
    val line =
        Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
    val area =
        Path().apply {
            addPath(line)
            lineTo(points.last().x, plot.bottom)
            lineTo(points.first().x, plot.bottom)
            close()
        }
    drawPath(area, color.copy(alpha = AREA_ALPHA))
    drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** An 8dp marker with a 2dp ring in the surface color so it stays legible where it crosses the line. */
private fun DrawScope.drawRingedDot(
    center: Offset,
    color: Color,
    surface: Color,
) {
    drawCircle(surface, radius = 6.dp.toPx(), center = center)
    drawCircle(color, radius = 4.dp.toPx(), center = center)
}

private const val AREA_ALPHA = 0.1f

/** Lets the pointer catch the first point from just left of the plot. */
private const val HIT_SLOP_PX = 8f
