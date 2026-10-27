package dev.dache.app.ui.chart

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText

/** Height of an axis label line as a multiple of its font size. */
const val LABEL_LINE_HEIGHT = 1.6f

/** Pixel geometry of a plot area inside a chart canvas. */
data class PlotArea(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun y(
        value: Double,
        scale: AxisScale,
    ): Float = bottom - (value / scale.max * height).toFloat()
}

/** Hairline gridlines at each tick, the baseline one step darker, and right-aligned tick labels. */
fun DrawScope.drawValueAxis(
    plot: PlotArea,
    scale: AxisScale,
    tickLabels: List<TextLayoutResult>,
    gridColor: Color,
    baselineColor: Color,
    labelGap: Float,
) {
    scale.ticks.forEachIndexed { index, tick ->
        val y = plot.y(tick, scale)
        drawLine(if (index == 0) baselineColor else gridColor, Offset(plot.left, y), Offset(plot.right, y), 1f)
        val label = tickLabels[index]
        drawText(label, topLeft = Offset(plot.left - labelGap - label.size.width, y - label.size.height / 2f))
    }
}

/**
 * Draws a subset of category labels under the plot so they never collide. Anchored on the newest (last)
 * position, then every [step]th one back.
 */
fun DrawScope.drawCategoryLabels(
    textMeasurer: TextMeasurer,
    labels: List<String>,
    xFor: (Int) -> Float,
    plot: PlotArea,
    style: TextStyle,
    gap: Float,
) {
    if (labels.isEmpty()) return
    val widest = labels.maxOf { textMeasurer.measure(it, style).size.width }
    val slot = if (labels.size > 1) plot.width / (labels.size - 1) else plot.width
    val step = maxOf(1, kotlin.math.ceil((widest + gap * 2) / slot).toInt())
    // Walk newest to oldest and skip any label that would touch the one already drawn to its right;
    // edge clamping can otherwise push neighbours into each other.
    var rightLimit = Float.MAX_VALUE
    labels.indices.reversed().filter { (labels.size - 1 - it) % step == 0 }.forEach { index ->
        val layout = textMeasurer.measure(labels[index], style)
        val x = (xFor(index) - layout.size.width / 2f).coerceIn(plot.left, size.width - layout.size.width)
        if (x + layout.size.width + gap <= rightLimit) {
            drawText(layout, topLeft = Offset(x, plot.bottom + gap))
            rightLimit = x
        }
    }
}

/** A rectangle whose top corners are rounded (the data end) and bottom corners square (the baseline). */
fun topRoundedRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
): Path {
    val r = minOf(radius, (bottom - top) / 2f, (right - left) / 2f)
    return Path().apply {
        addRoundRect(
            RoundRect(
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                topLeftCornerRadius = CornerRadius(r),
                topRightCornerRadius = CornerRadius(r),
                bottomLeftCornerRadius = CornerRadius.Zero,
                bottomRightCornerRadius = CornerRadius.Zero,
            ),
        )
    }
}

/** Same, rounded on the right end for horizontal bars. */
fun rightRoundedRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
): Path {
    val r = minOf(radius, (bottom - top) / 2f, (right - left) / 2f)
    return Path().apply {
        addRoundRect(
            RoundRect(
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                topLeftCornerRadius = CornerRadius.Zero,
                topRightCornerRadius = CornerRadius(r),
                bottomLeftCornerRadius = CornerRadius.Zero,
                bottomRightCornerRadius = CornerRadius(r),
            ),
        )
    }
}
