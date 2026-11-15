package dev.dache.app.boards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.action_cancel
import dev.dache.app.resources.board_add_widget
import dev.dache.app.resources.board_delete
import dev.dache.app.resources.board_delete_body
import dev.dache.app.resources.board_delete_title
import dev.dache.app.resources.board_done
import dev.dache.app.resources.board_edit
import dev.dache.app.resources.board_edit_hint
import dev.dache.app.resources.board_empty_body
import dev.dache.app.resources.board_empty_title
import dev.dache.app.resources.board_more
import dev.dache.app.resources.board_name_label
import dev.dache.app.resources.board_rename
import dev.dache.app.resources.board_rename_title
import dev.dache.app.resources.widget_save
import dev.dache.app.ui.components.EmptyState
import dev.dache.app.ui.layout.FormFactor
import dev.dache.app.ui.layout.LocalFormFactor
import dev.dache.app.ui.layout.PageScaffold
import dev.dache.core.boards.Board
import dev.dache.core.boards.Datapoint
import dev.dache.core.boards.Widget
import dev.dache.core.boards.WidgetData
import dev.dache.core.dashboard.DashboardState
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/** Everything the board screen changes; [BoardsViewModel] implements it in the app, tests pass their own. */
interface BoardActions {
    fun rename(name: String)

    fun delete()

    fun putWidget(widget: Widget)

    fun removeWidget(id: String)

    fun moveWidget(
        id: String,
        by: Int,
    )

    fun newWidgetId(): String
}

/**
 * A user-made dashboard. Viewing shows the widgets on a grid of up to four columns (fewer on narrow screens).
 * Edit mode, from the toolbar or a long press on a widget, keeps every widget live and adds controls to move,
 * resize, edit and remove it, and a button to add one.
 */
@Composable
fun BoardScreen(
    board: Board,
    state: DashboardState,
    actions: BoardActions,
    onRefresh: () -> Unit,
    onOpenMenu: (() -> Unit)?,
    startEditing: Boolean = false,
) {
    var editing by rememberSaveable(board.id) { mutableStateOf(startEditing || board.widgets.isEmpty()) }
    var editor by remember { mutableStateOf<Pair<Widget, Boolean>?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val now = remember(state.lastUpdated) { Clock.System.now() }
    val addWidget = { editor = Widget(actions.newWidgetId(), Datapoint.DEPLOYS) to true }

    PageScaffold(
        title = board.name,
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        onOpenMenu = onOpenMenu,
        lastUpdated = state.lastUpdated,
        actions = {
            if (editing) {
                IconButton(onClick = addWidget) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.board_add_widget))
                }
                IconButton(onClick = { editing = false }) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(Res.string.board_done))
                }
            } else {
                IconButton(onClick = { editing = true }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(Res.string.board_edit))
                }
            }
            BoardMenu(onRename = { renaming = true }, onDelete = { deleting = true })
        },
    ) {
        if (board.widgets.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Add,
                title = stringResource(Res.string.board_empty_title),
                body = stringResource(Res.string.board_empty_body),
            ) {
                OutlinedButton(onClick = addWidget) { Text(stringResource(Res.string.board_add_widget)) }
            }
        } else {
            if (!editing && LocalFormFactor.current == FormFactor.COMPACT) {
                Text(stringResource(Res.string.board_edit_hint), style = MaterialTheme.typography.bodySmall)
            }
            WidgetGrid(board.widgets) { widget, columns ->
                val value = remember(widget, state, now) { WidgetData.evaluate(widget, state, now) }
                val index = board.widgets.indexOf(widget)
                WidgetCard(
                    widget = widget,
                    value = value,
                    edit =
                        if (editing) {
                            WidgetEditActions(
                                onMoveEarlier = { actions.moveWidget(widget.id, -1) }.takeIf { index > 0 },
                                onMoveLater =
                                    { actions.moveWidget(widget.id, 1) }.takeIf {
                                        index <
                                            board.widgets.lastIndex
                                    },
                                onNarrower =
                                    { actions.putWidget(widget.copy(span = widget.span - 1)) }.takeIf {
                                        widget.span >
                                            1
                                    },
                                onWider =
                                    { actions.putWidget(widget.copy(span = widget.span + 1)) }
                                        .takeIf { widget.span < minOf(Widget.MAX_SPAN, columns) },
                                onEdit = { editor = widget to false },
                                onRemove = { actions.removeWidget(widget.id) },
                            )
                        } else {
                            null
                        },
                    onLongPress = if (editing) null else ({ editing = true }),
                )
            }
            if (editing) {
                OutlinedButton(onClick = addWidget) { Text(stringResource(Res.string.board_add_widget)) }
            }
        }
    }

    editor?.let { (widget, isNew) ->
        WidgetEditorDialog(
            initial = widget,
            isNew = isNew,
            state = state,
            onSave = actions::putWidget,
            onDismiss = { editor = null },
        )
    }
    if (renaming) {
        RenameDialog(board.name, onConfirm = actions::rename, onDismiss = { renaming = false })
    }
    if (deleting) {
        DeleteDialog(board.name, onConfirm = actions::delete, onDismiss = { deleting = false })
    }
}

/**
 * Lays [widgets] out in reading order on a grid of 1, 2 or 4 columns by width; each takes its span, capped at the
 * columns there are. [item] gets the column count, so resizing can stop at the widest that fits.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetGrid(
    widgets: List<Widget>,
    item: @Composable (Widget, columns: Int) -> Unit,
) {
    BoxWithConstraints {
        val columns =
            when {
                maxWidth < TWO_COLUMNS_FROM -> 1
                maxWidth < FOUR_COLUMNS_FROM -> 2
                else -> Widget.MAX_SPAN
            }
        val column = (maxWidth - GAP * (columns - 1)) / columns
        FlowRow(horizontalArrangement = Arrangement.spacedBy(GAP), verticalArrangement = Arrangement.spacedBy(GAP)) {
            widgets.forEach { widget ->
                val span = widget.span.coerceAtMost(columns)
                Box(Modifier.width(spanWidth(column, span))) { item(widget, columns) }
            }
        }
    }
}

private fun spanWidth(
    column: Dp,
    span: Int,
): Dp = column * span + GAP * (span - 1)

@Composable
private fun BoardMenu(
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.board_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(Res.string.board_rename)) }, onClick = {
                open = false
                onRename()
            })
            DropdownMenuItem(text = { Text(stringResource(Res.string.board_delete)) }, onClick = {
                open = false
                onDelete()
            })
        }
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.board_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(Board.MAX_NAME_LENGTH) },
                label = { Text(stringResource(Res.string.board_name_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name)
                    onDismiss()
                },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(Res.string.widget_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@Composable
private fun DeleteDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.board_delete_title, name)) },
        text = { Text(stringResource(Res.string.board_delete_body)) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(stringResource(Res.string.board_delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

private val GAP = 16.dp
private val TWO_COLUMNS_FROM = 560.dp
private val FOUR_COLUMNS_FROM = 1000.dp

/** [board]'s page, or nothing while it's gone (deleted, or another profile's) and the stack catches up. */
@Composable
fun BoardPage(
    board: Board?,
    state: DashboardState,
    viewModel: BoardsViewModel,
    onDeleted: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenMenu: (() -> Unit)?,
) {
    board ?: return
    key(board.id) {
        BoardScreen(
            board = board,
            state = state,
            actions = remember(board.id) { viewModel.actionsFor(board.id) { onDeleted(board.id) } },
            onRefresh = onRefresh,
            onOpenMenu = onOpenMenu,
        )
    }
}
