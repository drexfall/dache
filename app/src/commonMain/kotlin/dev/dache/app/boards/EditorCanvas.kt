package dev.dache.app.boards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.editor_canvas_empty
import dev.dache.app.resources.widget_select
import dev.dache.app.ui.theme.LocalChartColors
import dev.dache.core.boards.BoardGrid
import dev.dache.core.boards.Widget
import dev.dache.core.boards.WidgetData
import dev.dache.core.dashboard.DashboardState
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.time.Clock

/**
 * Where the canvas grid is on screen, so a drag that starts in the library can tell which cell it's over. The
 * canvas fills it in as it lays out.
 */
@Stable
class CanvasGeometry {
    internal var viewport: LayoutCoordinates? = null
    internal var grid: LayoutCoordinates? = null
    internal var metrics: GridMetrics? = null

    /** The cell under window position [window], or null when it's off the canvas. */
    fun cellAt(window: Offset): Pair<Int, Int>? {
        val viewport = viewport?.takeIf { it.isAttached }
        val grid = grid?.takeIf { it.isAttached }
        val metrics = metrics
        val onCanvas = viewport != null && viewport.boundsInWindow().contains(window)
        return if (onCanvas && grid != null && metrics != null) metrics.cellAt(grid.windowToLocal(window)) else null
    }
}

/** The handles a selected widget shows; each is a place to press. */
private enum class Handle { REMOVE, CORNER, MOVE, RIGHT, BOTTOM }

/**
 * The editor's middle pane: the board's widgets, live, on the same four-column grid the board uses, over faint
 * guides for every cell. A tap selects a widget; holding it (touch) or dragging it (mouse) moves it. The selected
 * widget shows handles: drag the grip to move it, the right or bottom edge or the corner to resize it, the cross
 * to remove it.
 */
@Composable
fun EditorCanvas(
    state: BoardEditorState,
    dashboard: DashboardState,
    geometry: CanvasGeometry,
    modifier: Modifier = Modifier,
) {
    val colors = LocalChartColors.current
    val density = LocalDensity.current
    val now = remember(dashboard.lastUpdated) { Clock.System.now() }
    BoxWithConstraints(
        modifier.background(colors.grid.copy(alpha = 0.35f)).onGloballyPositioned {
            geometry.viewport =
                it
        },
    ) {
        val available = maxWidth - CANVAS_PADDING * 2
        val gridWidth = maxOf(available, MIN_GRID_WIDTH)
        val metrics = remember(gridWidth, density) { GridMetrics.of(gridWidth, Widget.MAX_SPAN, density) }
        SideEffect { geometry.metrics = metrics }
        val placements = remember(state.widgets) { BoardGrid.place(state.widgets, Widget.MAX_SPAN) }
        // One spare row below the last widget, so there's always somewhere to drop at the end.
        val rows = maxOf(BoardGrid.rows(placements) + 1, MIN_ROWS)
        val padding = with(density) { CANVAS_PADDING.toPx() }
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .then(if (available < MIN_GRID_WIDTH) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
        ) {
            Box(
                Modifier
                    .width(gridWidth + CANVAS_PADDING * 2)
                    .pointerInput(
                        state,
                        metrics,
                    ) { canvasGestures(state, geometry, metrics, Offset(padding, padding)) },
            ) {
                Box(Modifier.padding(CANVAS_PADDING).width(gridWidth).onGloballyPositioned { geometry.grid = it }) {
                    GridGuides(metrics, rows)
                    if (state.widgets.isEmpty()) {
                        Text(
                            stringResource(Res.string.editor_canvas_empty),
                            modifier = Modifier.fillMaxWidth().padding(top = ROW_HEIGHT),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondaryText,
                        )
                    }
                    WidgetGrid(state.widgets, metrics, minRows = rows, placements = placements) { widget, _ ->
                        CanvasItem(
                            widget = widget,
                            selected = widget.id == state.selectedId,
                            dragging = widget.id == state.drag?.widget?.id,
                            dashboard = dashboard,
                            now = now,
                            onSelect = { state.select(widget.id) },
                        )
                    }
                    val selectedIndex = state.widgets.indexOfFirst { it.id == state.selectedId }
                    if (selectedIndex >= 0 && state.drag == null) {
                        SelectionFrame(metrics.rect(placements[selectedIndex]))
                    }
                }
            }
        }
    }
}

@Composable
private fun CanvasItem(
    widget: Widget,
    selected: Boolean,
    dragging: Boolean,
    dashboard: DashboardState,
    now: kotlin.time.Instant,
    onSelect: () -> Unit,
) {
    val colors = LocalChartColors.current
    val title = widget.displayTitle()
    val selectLabel = stringResource(Res.string.widget_select, title)
    if (dragging) {
        // Where the dragged widget will land.
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(colors.accent.copy(alpha = 0.12f), cornerRadius = CornerRadius(12.dp.toPx()))
            drawRoundRect(
                colors.accent,
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(PLACEHOLDER_DASH)),
            )
        }
        return
    }
    val value = remember(widget, dashboard, now) { WidgetData.evaluate(widget, dashboard, now) }
    Box(
        Modifier.fillMaxSize().semantics {
            this.selected = selected
            contentDescription = title
            onClick(selectLabel) {
                onSelect()
                true
            }
        },
    ) {
        WidgetCard(widget, value)
        // A cover over the live widget: in the editor a press selects or moves it, so the list rows and chart
        // marks under it mustn't open a resource or pin a value. The canvas still sees every press.
        Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
        if (selected) Box(Modifier.fillMaxSize().border(2.dp, colors.accent, RoundedCornerShape(12.dp)))
    }
}

/** A soft cell for every grid cell, so it's clear where widgets can go; widgets cover the ones they fill. */
@Composable
private fun GridGuides(
    metrics: GridMetrics,
    rows: Int,
) {
    val color = LocalChartColors.current.border
    val density = LocalDensity.current
    Canvas(Modifier.fillMaxWidth().height(with(density) { metrics.height(rows).toDp() })) {
        val corner = CornerRadius(12.dp.toPx())
        for (row in 0 until rows) {
            for (column in 0 until metrics.columns) {
                drawRoundRect(
                    color,
                    topLeft = Offset(metrics.x(column), metrics.y(row)),
                    size = Size(metrics.columnWidth, metrics.rowHeight),
                    cornerRadius = corner,
                )
            }
        }
    }
}

/** The selected widget's handles; they only draw, [canvasGestures] works out which one was pressed. */
@Composable
private fun SelectionFrame(rect: Rect) {
    val colors = LocalChartColors.current
    val density = LocalDensity.current
    Canvas(
        Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() }),
    ) {
        val w = size.width
        val h = size.height
        val accent = colors.accent
        val onAccent = colors.surface
        val pill = { center: Offset, pillSize: Size ->
            drawRoundRect(
                accent,
                topLeft = center - Offset(pillSize.width / 2, pillSize.height / 2),
                size = pillSize,
                cornerRadius = CornerRadius(minOf(pillSize.width, pillSize.height) / 2),
            )
        }
        // Grip: a pill on the top edge with two rows of dots.
        pill(Offset(w / 2, 0f), Size(44.dp.toPx(), 18.dp.toPx()))
        for (dx in -1..1) {
            for (dy in listOf(-1, 1)) {
                drawCircle(onAccent, 1.6.dp.toPx(), Offset(w / 2 + dx * 7.dp.toPx(), dy * 3.dp.toPx()))
            }
        }
        // Resize: the right edge, the bottom edge, the corner.
        pill(Offset(w, h / 2), Size(8.dp.toPx(), 40.dp.toPx()))
        pill(Offset(w / 2, h), Size(40.dp.toPx(), 8.dp.toPx()))
        drawCircle(accent, 8.dp.toPx(), Offset(w, h))
        drawCircle(onAccent, 3.dp.toPx(), Offset(w, h))
        // Remove: a cross on the top-right corner.
        drawCircle(colors.critical, 12.dp.toPx(), Offset(w, 0f))
        val arm = 4.5.dp.toPx()
        val stroke = 2.dp.toPx()
        drawLine(onAccent, Offset(w - arm, -arm), Offset(w + arm, arm), stroke, StrokeCap.Round)
        drawLine(onAccent, Offset(w - arm, arm), Offset(w + arm, -arm), stroke, StrokeCap.Round)
    }
}

/** Which handle of a selected widget at [rect] is at [point], if any; checked in order of priority. */
private fun Density.handleAt(
    point: Offset,
    rect: Rect,
): Handle? {
    val near = { center: Offset, halfWidth: Float, halfHeight: Float ->
        point.x in center.x - halfWidth..center.x + halfWidth && point.y in center.y - halfHeight..center.y + halfHeight
    }
    val reach = 20.dp.toPx()
    val edge = 12.dp.toPx()
    return when {
        near(rect.topRight, reach, reach) -> Handle.REMOVE
        near(rect.bottomRight, reach, reach) -> Handle.CORNER
        near(Offset(rect.center.x, rect.top), 32.dp.toPx(), reach) -> Handle.MOVE
        near(Offset(rect.right, rect.center.y), edge, rect.height / 2) -> Handle.RIGHT
        near(Offset(rect.center.x, rect.bottom), rect.width / 2, edge) -> Handle.BOTTOM
        else -> null
    }
}

/**
 * Every press on the canvas: handles of the selected widget first, then widgets (tap to select, hold or drag to
 * move), then empty space (tap to deselect). [origin] is where the grid starts inside the pressed area.
 */
private suspend fun PointerInputScope.canvasGestures(
    state: BoardEditorState,
    geometry: CanvasGeometry,
    metrics: GridMetrics,
    origin: Offset,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val point = down.position - origin
        val widgets = state.widgets
        val placements = BoardGrid.place(widgets, Widget.MAX_SPAN)
        val selected = widgets.indexOfFirst { it.id == state.selectedId }
        val selectedRect = placements.getOrNull(selected)?.let { metrics.rect(it) }
        val handle = selectedRect?.let { handleAt(point, it) }
        val press = CanvasPress(state, geometry, metrics, origin, down)
        if (handle != null && selectedRect != null) {
            down.consume()
            with(press) { onHandle(handle, widgets[selected], selectedRect) }
        } else {
            val hit = placements.indexOfFirst { metrics.rect(it).contains(point) }
            when (awaitPressOutcome(down)) {
                PressOutcome.TAP -> state.select(widgets.getOrNull(hit)?.id)
                PressOutcome.DRAG -> if (hit >= 0) with(press) { move(widgets[hit], metrics.rect(placements[hit])) }
                PressOutcome.SCROLL -> Unit
            }
        }
    }
}

/** One press on the canvas that turned into moving, resizing or removing a widget. */
private class CanvasPress(
    val state: BoardEditorState,
    val geometry: CanvasGeometry,
    val metrics: GridMetrics,
    val origin: Offset,
    val down: PointerInputChange,
) {
    private val point = down.position - origin

    suspend fun AwaitPointerEventScope.onHandle(
        handle: Handle,
        widget: Widget,
        rect: Rect,
    ) {
        when (handle) {
            Handle.REMOVE -> if (waitForUpOrCancellation() != null) state.remove(widget.id)
            Handle.MOVE -> move(widget, rect)
            Handle.RIGHT, Handle.BOTTOM, Handle.CORNER -> resize(handle, widget, rect)
        }
    }

    /** Drags [widget], drawn at [rect], until the pointer lifts. */
    suspend fun AwaitPointerEventScope.move(
        widget: Widget,
        rect: Rect,
    ) {
        val grid = geometry.grid ?: return
        state.startDrag(
            widget,
            isNew = false,
            pointer = grid.localToWindow(point),
            grab = point - rect.topLeft,
            size = rect.size,
        )
        followPointer(down.id) { change ->
            val position = change.position - origin
            state.dragTo(grid.localToWindow(position), metrics.cellAt(position), Widget.MAX_SPAN)
        }
        state.endDrag()
    }

    /** Resizes [widget] from [handle], snapping to whole columns and rows, until the pointer lifts. */
    private suspend fun AwaitPointerEventScope.resize(
        handle: Handle,
        widget: Widget,
        rect: Rect,
    ) {
        var moved = Offset.Zero
        followPointer(down.id) { change ->
            moved += change.positionChange()
            val width = if (handle == Handle.BOTTOM) rect.width else rect.width + moved.x
            val height = if (handle == Handle.RIGHT) rect.height else rect.height + moved.y
            state.resize(widget.id, metrics.spanFor(width), metrics.rowsFor(height))
        }
        state.widgets.firstOrNull { it.id == widget.id }?.let(state::update)
    }
}

private val CANVAS_PADDING = 24.dp

/** Narrower than this, the canvas scrolls sideways rather than squeeze four columns. */
private val MIN_GRID_WIDTH = 480.dp
private const val MIN_ROWS = 4
private val PLACEHOLDER_DASH = floatArrayOf(12f, 8f)
