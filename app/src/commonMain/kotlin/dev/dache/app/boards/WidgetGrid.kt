package dev.dache.app.boards

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.dache.core.boards.BoardGrid
import dev.dache.core.boards.Placement
import dev.dache.core.boards.Widget
import kotlin.math.floor
import kotlin.math.roundToInt

/** Height of one grid row; a widget [Widget.effectiveHeight] rows tall is that many of these plus the gaps. */
val ROW_HEIGHT = 112.dp

/** Space between widgets, both ways. */
val GRID_GAP = 16.dp

/** Sizes on a grid of [columns], in pixels, so gestures can turn a pointer position into a cell or a size. */
@Immutable
class GridMetrics(
    val columns: Int,
    val columnWidth: Float,
    val rowHeight: Float,
    val gap: Float,
) {
    fun x(column: Int): Float = column * (columnWidth + gap)

    fun y(row: Int): Float = row * (rowHeight + gap)

    fun width(span: Int): Float = span * columnWidth + (span - 1) * gap

    fun height(rows: Int): Float = rows * rowHeight + (rows - 1).coerceAtLeast(0) * gap

    fun rect(placement: Placement): Rect =
        Rect(
            Offset(x(placement.column), y(placement.row)),
            Size(width(placement.span), height(placement.height)),
        )

    /** The cell under [offset]; columns are clamped to the grid, rows only from below. */
    fun cellAt(offset: Offset): Pair<Int, Int> =
        floor(offset.x / (columnWidth + gap)).toInt().coerceIn(0, columns - 1) to
            floor(offset.y / (rowHeight + gap)).toInt().coerceAtLeast(0)

    /** The span whose width is closest to [width] pixels. */
    fun spanFor(width: Float): Int = ((width + gap) / (columnWidth + gap)).roundToInt().coerceIn(1, columns)

    /** The height in rows closest to [height] pixels. */
    fun rowsFor(height: Float): Int = ((height + gap) / (rowHeight + gap)).roundToInt().coerceIn(1, Widget.MAX_HEIGHT)

    companion object {
        /** Metrics for a grid [width] wide with [columns] columns. */
        fun of(
            width: Dp,
            columns: Int,
            density: Density,
        ): GridMetrics =
            with(density) {
                val gap = GRID_GAP.toPx()
                GridMetrics(columns, (width.toPx() - gap * (columns - 1)) / columns, ROW_HEIGHT.toPx(), gap)
            }
    }
}

/**
 * Draws [widgets] where [BoardGrid] places them on [metrics]' columns, at least [minRows] tall. Each [item] gets
 * a box of exactly its size, and slides to a new spot when the layout changes, e.g. while the editor reorders.
 */
@Composable
fun WidgetGrid(
    widgets: List<Widget>,
    metrics: GridMetrics,
    modifier: Modifier = Modifier,
    minRows: Int = 0,
    placements: List<Placement> = remember(widgets, metrics.columns) { BoardGrid.place(widgets, metrics.columns) },
    item: @Composable (Widget, Placement) -> Unit,
) {
    val density = LocalDensity.current
    val rows = maxOf(BoardGrid.rows(placements), minRows)
    Box(modifier.fillMaxWidth().height(with(density) { metrics.height(rows).coerceAtLeast(0f).toDp() })) {
        widgets.forEachIndexed { index, widget ->
            val placement = placements[index]
            key(widget.id) {
                val target = IntOffset(metrics.x(placement.column).roundToInt(), metrics.y(placement.row).roundToInt())
                val offset by animateIntOffsetAsState(target, label = "widget position")
                Box(
                    Modifier
                        .offset { offset }
                        .size(
                            with(density) { metrics.width(placement.span).toDp() },
                            with(density) { metrics.height(placement.height).toDp() },
                        ),
                ) { item(widget, placement) }
            }
        }
    }
}
