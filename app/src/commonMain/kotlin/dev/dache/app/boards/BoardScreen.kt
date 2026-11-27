package dev.dache.app.boards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.dache.app.dashboard.ProviderErrorCard
import dev.dache.app.navigation.LocalAppNavigator
import dev.dache.app.resources.Res
import dev.dache.app.resources.action_cancel
import dev.dache.app.resources.action_connect_account
import dev.dache.app.resources.board_customize
import dev.dache.app.resources.board_customize_title
import dev.dache.app.resources.board_delete
import dev.dache.app.resources.board_delete_body
import dev.dache.app.resources.board_delete_title
import dev.dache.app.resources.board_edit
import dev.dache.app.resources.board_edit_hint
import dev.dache.app.resources.board_empty_body
import dev.dache.app.resources.board_empty_title
import dev.dache.app.resources.board_more
import dev.dache.app.resources.board_name_label
import dev.dache.app.resources.board_open_editor
import dev.dache.app.resources.overview_empty_body
import dev.dache.app.resources.overview_empty_title
import dev.dache.app.resources.widget_save
import dev.dache.app.status.StatusBanner
import dev.dache.app.ui.components.EmptyState
import dev.dache.app.ui.layout.FormFactor
import dev.dache.app.ui.layout.LocalFormFactor
import dev.dache.app.ui.layout.PageScaffold
import dev.dache.core.boards.Board
import dev.dache.core.boards.BoardIcon
import dev.dache.core.boards.Widget
import dev.dache.core.boards.WidgetData
import dev.dache.core.dashboard.DashboardState
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/** Everything the board screen and its editor change; [BoardsViewModel] implements it, tests pass their own. */
interface BoardActions {
    fun rename(name: String)

    fun setIcon(icon: BoardIcon)

    fun delete()

    /** Saves the board's widgets in this order, each made valid. */
    fun saveWidgets(widgets: List<Widget>)

    fun newWidgetId(): String
}

/**
 * A board: its widgets, live, on a grid of up to four columns (fewer on narrow screens), under any provider
 * incidents and failed refreshes. The edit button, or a long press on a widget, opens the editor through [onEdit]
 * with that widget selected. With no accounts connected it asks for one through [onConnectAccount] instead.
 * [canDelete] is false for the profile's last board, which has to stay.
 */
@Composable
fun BoardScreen(
    board: Board,
    state: DashboardState,
    actions: BoardActions,
    onEdit: (widgetId: String?) -> Unit,
    onRefresh: () -> Unit,
    onOpenMenu: (() -> Unit)?,
    canDelete: Boolean = true,
    onConnectAccount: (() -> Unit)? = null,
) {
    var customizing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val now = remember(state.lastUpdated) { Clock.System.now() }

    PageScaffold(
        title = board.name,
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        onOpenMenu = onOpenMenu,
        lastUpdated = state.lastUpdated,
        actions = {
            IconButton(onClick = { onEdit(null) }) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(Res.string.board_edit))
            }
            BoardMenu(onCustomize = { customizing = true }, onDelete = { deleting = true }.takeIf { canDelete })
        },
    ) {
        if (state.accounts.isEmpty() && onConnectAccount != null) {
            EmptyState(
                icon = Icons.Filled.Home,
                title = stringResource(Res.string.overview_empty_title),
                body = stringResource(Res.string.overview_empty_body),
            ) {
                Button(onClick = onConnectAccount) { Text(stringResource(Res.string.action_connect_account)) }
            }
            return@PageScaffold
        }
        val navigator = LocalAppNavigator.current
        StatusBanner(state.statuses.values) { status ->
            state.accounts.firstOrNull { it.providerId == status.providerId }?.let { navigator.openAccount(it.id) }
        }
        state.results.filter { it.error != null }.forEach { ProviderErrorCard(it, onRefresh) }
        if (board.widgets.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Add,
                title = stringResource(Res.string.board_empty_title),
                body = stringResource(Res.string.board_empty_body),
            ) {
                OutlinedButton(onClick = { onEdit(null) }) { Text(stringResource(Res.string.board_open_editor)) }
            }
        } else {
            if (LocalFormFactor.current == FormFactor.COMPACT) {
                Text(stringResource(Res.string.board_edit_hint), style = MaterialTheme.typography.bodySmall)
            }
            BoxWithConstraints {
                val columns =
                    when {
                        maxWidth < TWO_COLUMNS_FROM -> 1
                        maxWidth < FOUR_COLUMNS_FROM -> 2
                        else -> Widget.MAX_SPAN
                    }
                val metrics = GridMetrics.of(maxWidth, columns, LocalDensity.current)
                WidgetGrid(board.widgets, metrics) { widget, _ ->
                    val value = remember(widget, state, now) { WidgetData.evaluate(widget, state, now) }
                    WidgetCard(widget = widget, value = value, onLongPress = { onEdit(widget.id) })
                }
            }
        }
    }

    if (customizing) {
        CustomizeDialog(
            board,
            onConfirm = { name, icon ->
                actions.rename(name)
                actions.setIcon(icon)
            },
            onDismiss = { customizing = false },
        )
    }
    if (deleting) {
        DeleteDialog(board.name, onConfirm = actions::delete, onDismiss = { deleting = false })
    }
}

/** Name and icon, and delete unless [onDelete] is null: the last board can't be deleted. */
@Composable
private fun BoardMenu(
    onCustomize: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.board_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(Res.string.board_customize)) }, onClick = {
                open = false
                onCustomize()
            })
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.board_delete)) },
                onClick = {
                    open = false
                    onDelete?.invoke()
                },
                enabled = onDelete != null,
            )
        }
    }
}

/** [board]'s name and icon, both applied together on save. */
@Composable
private fun CustomizeDialog(
    board: Board,
    onConfirm: (String, BoardIcon) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(board.name) }
    var icon by remember { mutableStateOf(board.icon ?: BoardIcon.ofSymbol(symbolOf(null).id)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.board_customize_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(Board.MAX_NAME_LENGTH) },
                    label = { Text(stringResource(Res.string.board_name_label)) },
                    leadingIcon = { BoardIconImage(icon) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                BoardIconPicker(icon, onSelect = { icon = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name, icon)
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

private val TWO_COLUMNS_FROM = 560.dp
private val FOUR_COLUMNS_FROM = 1000.dp

/** [board]'s page, or nothing while it's gone (deleted, or another profile's) and the stack catches up. */
@Composable
fun BoardPage(
    board: Board?,
    state: DashboardState,
    viewModel: BoardsViewModel,
    canDelete: Boolean,
    onEdit: (widgetId: String?) -> Unit,
    onDeleted: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenMenu: (() -> Unit)?,
    onConnectAccount: () -> Unit,
) {
    board ?: return
    key(board.id) {
        BoardScreen(
            board = board,
            state = state,
            actions = remember(board.id) { viewModel.actionsFor(board.id) { onDeleted(board.id) } },
            onEdit = onEdit,
            onRefresh = onRefresh,
            onOpenMenu = onOpenMenu,
            canDelete = canDelete,
            onConnectAccount = onConnectAccount,
        )
    }
}
