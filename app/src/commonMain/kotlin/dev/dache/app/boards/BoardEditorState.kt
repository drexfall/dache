package dev.dache.app.boards

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.dache.core.boards.BoardGrid
import dev.dache.core.boards.Widget

/**
 * A widget being dragged: one already on the board, or a new one from the library ([isNew]) that joins the board
 * once it's over the canvas. [pointer] is in window coordinates; the ghost is drawn [grab] up and left of it.
 */
data class EditorDrag(
    val widget: Widget,
    val isNew: Boolean,
    val pointer: Offset,
    val grab: Offset,
    val size: Size,
    val cell: Pair<Int, Int>? = null,
)

/**
 * The editor's working copy of a board's widgets. Every finished change goes to [save]; a drag or resize in
 * progress only changes the copy, so the board is written once when it ends.
 */
@Stable
class BoardEditorState(
    initial: List<Widget>,
    selected: String?,
    private val save: (List<Widget>) -> Unit,
    private val newId: () -> String,
) {
    var widgets by mutableStateOf(initial.map { it.normalized() })
        private set

    var selectedId by mutableStateOf(selected?.takeIf { id -> initial.any { it.id == id } })
        private set

    var drag by mutableStateOf<EditorDrag?>(null)
        private set

    val selected: Widget? get() = widgets.firstOrNull { it.id == selectedId }

    fun select(id: String?) {
        selectedId = id
    }

    /** [widget] in place of the one with its id. */
    fun update(widget: Widget) = commit(widgets.map { if (it.id == widget.id) widget.normalized() else it })

    fun remove(id: String) {
        if (selectedId == id) selectedId = null
        commit(widgets.filterNot { it.id == id })
    }

    /** Moves widget [id] [by] places in reading order, stopping at either end. */
    fun move(
        id: String,
        by: Int,
    ) {
        val from = widgets.indexOfFirst { it.id == id }
        if (from < 0) return
        val to = (from + by).coerceIn(0, widgets.lastIndex)
        commit(widgets.toMutableList().apply { add(to, removeAt(from)) })
    }

    /** Adds a copy of library [template] at the end and selects it. */
    fun add(template: Widget) {
        val widget = template.copy(id = newId()).normalized()
        selectedId = widget.id
        commit(widgets + widget)
    }

    /** Resizes widget [id] while a handle is dragged, without saving; [update] it when the drag ends. */
    fun resize(
        id: String,
        span: Int,
        height: Int,
    ) {
        widgets =
            widgets.map {
                if (it.id == id && (it.span != span || it.effectiveHeight != height)) {
                    it.copy(span = span, height = height).normalized()
                } else {
                    it
                }
            }
    }

    /**
     * Starts dragging [widget]: one on the board, or a library template when [isNew], which gets an id of its own
     * and joins the board once it's over the canvas.
     */
    fun startDrag(
        widget: Widget,
        isNew: Boolean,
        pointer: Offset,
        grab: Offset,
        size: Size,
    ) {
        val dragged = if (isNew) widget.copy(id = newId()).normalized() else widget
        if (!isNew) selectedId = widget.id
        drag = EditorDrag(dragged, isNew, pointer, grab, size)
    }

    /**
     * Follows the pointer to [pointer], over grid [cell] of a [columns]-wide canvas, or off it when [cell] is
     * null. A new widget off the canvas leaves the board again; otherwise the dragged widget moves each time the
     * pointer reaches another cell.
     */
    fun dragTo(
        pointer: Offset,
        cell: Pair<Int, Int>?,
        columns: Int,
    ) {
        val current = drag ?: return
        drag = current.copy(pointer = pointer, cell = cell)
        val onBoard = widgets.any { it.id == current.widget.id }
        when {
            cell == null -> if (current.isNew) widgets = widgets.filterNot { it.id == current.widget.id }
            cell != current.cell || !onBoard -> widgets = placedAt(widgets, current.widget, cell, columns)
        }
    }

    /** Drops the dragged widget where it is; a new one is kept only if it made it onto the canvas. */
    fun endDrag() {
        val current = drag ?: return
        drag = null
        if (widgets.any { it.id == current.widget.id }) {
            selectedId = current.widget.id
            save(widgets)
        }
    }

    private fun commit(next: List<Widget>) {
        widgets = next
        save(next)
    }
}

/**
 * [widgets] with [dragged] moved to grid [cell], or added there if it isn't on the board yet. Over another widget
 * it takes that widget's place in reading order; over an empty cell it goes where reading order puts the cell.
 */
private fun placedAt(
    widgets: List<Widget>,
    dragged: Widget,
    cell: Pair<Int, Int>,
    columns: Int,
): List<Widget> {
    val placements = BoardGrid.place(widgets, columns)
    val from = widgets.indexOfFirst { it.id == dragged.id }
    val (column, row) = cell
    val hit = BoardGrid.hit(placements, column, row)
    if (hit != null && hit == from) return widgets
    val others = widgets.filterNot { it.id == dragged.id }.toMutableList()
    val to = hit ?: BoardGrid.insertionIndex(placements.filterIndexed { index, _ -> index != from }, column, row)
    others.add(to.coerceIn(0, others.size), dragged)
    return others
}
