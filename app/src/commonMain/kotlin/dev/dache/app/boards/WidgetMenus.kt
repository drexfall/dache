package dev.dache.app.boards

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import dev.dache.app.resources.Res
import dev.dache.app.resources.board_customize
import dev.dache.app.resources.board_open_editor
import dev.dache.app.resources.context_move_earlier
import dev.dache.app.resources.context_move_later
import dev.dache.app.resources.context_widget_duplicate
import dev.dache.app.resources.context_widget_edit
import dev.dache.app.resources.context_widget_remove
import dev.dache.app.ui.components.ContextMenuItem
import dev.dache.core.boards.Board
import dev.dache.core.boards.Widget
import org.jetbrains.compose.resources.stringResource

/**
 * A right click on [widget] on the board: edit it, copy it to the end of the board or remove it, then edit the
 * whole board or its name and icon. The same things a touch user reaches through a long press and the top bar.
 */
@Composable
internal fun boardWidgetMenu(
    widget: Widget,
    board: Board,
    actions: BoardActions,
    onEdit: (widgetId: String?) -> Unit,
    onCustomize: () -> Unit,
): List<ContextMenuItem> =
    listOf(
        ContextMenuItem(stringResource(Res.string.context_widget_edit), icon = Icons.Filled.Edit) {
            onEdit(widget.id)
        },
        ContextMenuItem(stringResource(Res.string.context_widget_duplicate)) {
            actions.saveWidgets(board.widgets + widget.copy(id = actions.newWidgetId()))
        },
        ContextMenuItem(
            stringResource(Res.string.context_widget_remove),
            icon = Icons.Filled.Delete,
            destructive = true,
        ) {
            actions.saveWidgets(board.widgets.filterNot { it.id == widget.id })
        },
        ContextMenuItem(stringResource(Res.string.board_open_editor), startsGroup = true) { onEdit(null) },
        ContextMenuItem(stringResource(Res.string.board_customize), onClick = onCustomize),
    )

/** A right click on [widget] in the editor: copy it, move it along the board, or remove it. */
@Composable
internal fun canvasWidgetMenu(
    widget: Widget,
    state: BoardEditorState,
): List<ContextMenuItem> {
    val index = state.widgets.indexOfFirst { it.id == widget.id }
    return listOf(
        ContextMenuItem(stringResource(Res.string.context_widget_duplicate)) { state.add(widget) },
        ContextMenuItem(stringResource(Res.string.context_move_earlier), enabled = index > 0) {
            state.move(widget.id, -1)
        },
        ContextMenuItem(
            stringResource(Res.string.context_move_later),
            enabled =
                index in 0 until state.widgets.lastIndex,
        ) {
            state.move(widget.id, 1)
        },
        // No icons here: one on remove alone would leave the other entries indented around empty slots.
        ContextMenuItem(stringResource(Res.string.context_widget_remove), destructive = true, startsGroup = true) {
            state.remove(widget.id)
        },
    )
}
