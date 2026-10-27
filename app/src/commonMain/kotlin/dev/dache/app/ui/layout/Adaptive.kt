package dev.dache.app.ui.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How the app presents itself. Decided by window width, not platform: a desktop window dragged narrow gets
 * the phone layout, a tablet in landscape gets the desktop one.
 */
enum class FormFactor {
    /** Phones: modal sidebar behind a menu button, one column, pull to refresh, bottom sheets. */
    COMPACT,

    /** Desktop and large tablets: permanent sidebar, multi-column grid, hover readouts, dialogs. */
    EXPANDED,
}

val EXPANDED_MIN_WIDTH: Dp = 840.dp

val LocalFormFactor = staticCompositionLocalOf { FormFactor.COMPACT }

/**
 * Lays [items] out in as many equal columns as fit at [minCellWidth] (capped at [maxColumns]). Cells in a
 * row align to the top; heights can differ.
 */
@Composable
fun AdaptiveGrid(
    items: List<@Composable () -> Unit>,
    minCellWidth: Dp,
    modifier: Modifier = Modifier,
    maxColumns: Int = 3,
    spacing: Dp = 16.dp,
) {
    if (items.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val fit = ((maxWidth + spacing) / (minCellWidth + spacing)).toInt()
        val columns = fit.coerceIn(1, maxColumns).coerceAtMost(items.size)
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            items.chunked(columns).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing), verticalAlignment = Alignment.Top) {
                    rowItems.forEach { item -> Column(Modifier.weight(1f)) { item() } }
                    // Keep cell widths equal on a short last row.
                    repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
