package dev.dache.app.boards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.editor_add_widget
import dev.dache.app.resources.editor_category_cost
import dev.dache.app.resources.editor_category_delivery
import dev.dache.app.resources.editor_category_monitoring
import dev.dache.app.resources.editor_category_resources
import dev.dache.app.resources.editor_library
import dev.dache.app.resources.editor_library_hint
import dev.dache.app.ui.theme.LocalChartColors
import dev.dache.core.boards.Datapoint
import dev.dache.core.boards.Widget
import dev.dache.core.boards.WidgetChart
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** One widget in the library: a datapoint drawn a particular way, at a sensible starting width. */
data class LibraryEntry(
    val datapoint: Datapoint,
    val chart: WidgetChart,
) {
    fun template(): Widget = Widget("", datapoint, chart, span = if (chart == WidgetChart.STAT) 1 else 2).normalized()
}

/** The library's sections, each with the widgets most worth starting from. */
val WIDGET_LIBRARY: List<Pair<StringResource, List<LibraryEntry>>> =
    listOf(
        Res.string.editor_category_delivery to
            listOf(
                LibraryEntry(Datapoint.DEPLOYS, WidgetChart.BARS),
                LibraryEntry(Datapoint.FAILED_DEPLOYS, WidgetChart.STAT),
                LibraryEntry(Datapoint.CI_RUNS, WidgetChart.BARS),
                LibraryEntry(Datapoint.FAILED_CI_RUNS, WidgetChart.STAT),
            ),
        Res.string.editor_category_monitoring to
            listOf(
                LibraryEntry(Datapoint.UPTIME, WidgetChart.STRIP),
                LibraryEntry(Datapoint.RESPONSE_TIME, WidgetChart.LINE),
                LibraryEntry(Datapoint.MONITORS, WidgetChart.LIST),
                LibraryEntry(Datapoint.MONITORS_DOWN, WidgetChart.STAT),
            ),
        Res.string.editor_category_resources to
            listOf(
                LibraryEntry(Datapoint.SITES, WidgetChart.LIST),
                LibraryEntry(Datapoint.FAILING_SITES, WidgetChart.LIST),
                LibraryEntry(Datapoint.DOMAINS, WidgetChart.STAT),
                LibraryEntry(Datapoint.EXPIRING_DOMAINS, WidgetChart.LIST),
                LibraryEntry(Datapoint.SERVERS, WidgetChart.LIST),
                LibraryEntry(Datapoint.WORKERS, WidgetChart.STAT),
            ),
        Res.string.editor_category_cost to
            listOf(
                LibraryEntry(Datapoint.DAILY_COST, WidgetChart.BARS),
                LibraryEntry(Datapoint.DAILY_COST, WidgetChart.STAT),
            ),
    )

/**
 * The widgets to build a board from. Drag one onto the canvas to place it, or tap it to add it at the end. A side
 * panel on wide screens; a strip along the bottom, scrolling sideways, on narrow ones ([horizontal]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetLibrary(
    state: BoardEditorState,
    geometry: CanvasGeometry,
    horizontal: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalChartColors.current
    val header: @Composable () -> Unit = {
        Text(stringResource(Res.string.editor_library), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(Res.string.editor_library_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondaryText,
        )
    }
    if (horizontal) {
        Column(modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.padding(horizontal = 16.dp)) { header() }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                WIDGET_LIBRARY.forEach { (category, entries) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CategoryLabel(category)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            entries.forEach { LibraryTile(it, state, geometry, Modifier.width(COMPACT_TILE_WIDTH)) }
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column { header() }
            WIDGET_LIBRARY.forEach { (category, entries) ->
                CategoryLabel(category)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 2,
                ) {
                    entries.forEach { LibraryTile(it, state, geometry, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CategoryLabel(label: StringResource) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.labelMedium,
        color = LocalChartColors.current.secondaryText,
    )
}

/** Holds a tile's position on screen for its drag gesture, without recomposing when it changes. */
private class CoordinatesHolder {
    var value: LayoutCoordinates? = null
}

@Composable
private fun LibraryTile(
    entry: LibraryEntry,
    state: BoardEditorState,
    geometry: CanvasGeometry,
    modifier: Modifier = Modifier,
) {
    val colors = LocalChartColors.current
    val name = stringResource(entry.datapoint.label)
    val chartName = stringResource(entry.chart.label)
    val description = stringResource(Res.string.editor_add_widget, name, chartName)
    val coordinates = remember { CoordinatesHolder() }
    Surface(
        modifier =
            modifier
                .onGloballyPositioned { coordinates.value = it }
                .pointerInput(entry, state, geometry) { libraryGestures(entry, state, geometry, coordinates) }
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                    onClick {
                        state.add(entry.template())
                        true
                    }
                },
        shape = RoundedCornerShape(10.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ChartGlyph(entry.chart, Modifier.fillMaxWidth().height(28.dp))
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                color = colors.primaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(chartName, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText, maxLines = 1)
        }
    }
}

/** Tap adds the widget at the end; a drag carries a copy over the canvas and drops it where the pointer lifts. */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.libraryGestures(
    entry: LibraryEntry,
    state: BoardEditorState,
    geometry: CanvasGeometry,
    coordinates: CoordinatesHolder,
) {
    awaitEachGesture {
        val down = awaitFirstDown()
        when (awaitPressOutcome(down)) {
            PressOutcome.TAP -> state.add(entry.template())
            PressOutcome.DRAG -> {
                val tile = coordinates.value ?: return@awaitEachGesture
                val template = entry.template()
                val metrics = geometry.metrics
                val size =
                    metrics?.let { Size(it.width(template.span), it.height(template.effectiveHeight)) }
                        ?: Size(GHOST_FALLBACK.toPx(), GHOST_FALLBACK.toPx())
                val start = tile.localToWindow(down.position)
                val grab = Offset(minOf(size.width / 2, 64.dp.toPx()), 24.dp.toPx())
                state.startDrag(template, isNew = true, pointer = start, grab = grab, size = size)
                state.dragTo(start, geometry.cellAt(start), Widget.MAX_SPAN)
                followPointer(down.id) { change ->
                    val window = tile.localToWindow(change.position)
                    state.dragTo(window, geometry.cellAt(window), Widget.MAX_SPAN)
                }
                state.endDrag()
            }
            PressOutcome.SCROLL -> Unit
        }
    }
}

/** A tiny picture of how [chart] draws, so tiles read at a glance. */
@Composable
private fun ChartGlyph(
    chart: WidgetChart,
    modifier: Modifier = Modifier,
) {
    val colors = LocalChartColors.current
    if (chart == WidgetChart.STAT) {
        Text("42", modifier = modifier, style = MaterialTheme.typography.titleLarge, color = colors.accent)
        return
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (chart) {
            WidgetChart.BARS -> {
                val heights = GLYPH_BARS
                val bar = w / (heights.size * 2f)
                heights.forEachIndexed { i, f ->
                    drawRoundRect(
                        colors.accent,
                        topLeft = Offset(i * bar * 2 + bar / 2, h * (1 - f)),
                        size = Size(bar, h * f),
                        cornerRadius = CornerRadius(1.5.dp.toPx()),
                    )
                }
            }
            WidgetChart.LINE -> {
                val points = GLYPH_LINE
                val path = Path()
                points.forEachIndexed { i, f ->
                    val x = w * i / (points.size - 1)
                    if (i == 0) path.moveTo(x, h * f) else path.lineTo(x, h * f)
                }
                drawPath(path, colors.accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
            WidgetChart.LIST ->
                for (i in 0 until GLYPH_LIST_ROWS) {
                    val y = h * (i + GLYPH_ROW_MIDDLE) / GLYPH_LIST_ROWS
                    drawCircle(if (i == 0) colors.critical else colors.accent, 2.5.dp.toPx(), Offset(4.dp.toPx(), y))
                    drawLine(
                        colors.quiet,
                        Offset(10.dp.toPx(), y),
                        Offset(w * GLYPH_LIST_LINE, y),
                        3.dp.toPx(),
                        StrokeCap.Round,
                    )
                }
            WidgetChart.STRIP -> {
                val cells = GLYPH_STRIP_DAYS
                val gap = 2.dp.toPx()
                val cell = (w - gap * (cells - 1)) / cells
                for (row in 0..1) {
                    for (i in 0 until cells) {
                        drawRoundRect(
                            if (row == 0 && i == GLYPH_STRIP_DOWN_DAY) colors.critical else colors.quiet,
                            topLeft = Offset(i * (cell + gap), row * h / 2),
                            size = Size(cell, h / 2 - gap),
                            cornerRadius = CornerRadius(1.5.dp.toPx()),
                        )
                    }
                }
            }
            WidgetChart.STAT -> Unit
        }
    }
}

private val COMPACT_TILE_WIDTH = 112.dp

// The glyphs' sample shapes, as fractions of their height.
private val GLYPH_BARS = listOf(0.4f, 0.7f, 0.5f, 0.9f, 0.6f, 0.8f)
private val GLYPH_LINE = listOf(0.6f, 0.4f, 0.55f, 0.3f, 0.45f, 0.15f)
private const val GLYPH_LIST_ROWS = 3
private const val GLYPH_ROW_MIDDLE = 0.5f
private const val GLYPH_LIST_LINE = 0.8f
private const val GLYPH_STRIP_DAYS = 10
private const val GLYPH_STRIP_DOWN_DAY = 6
private val GHOST_FALLBACK = 160.dp
