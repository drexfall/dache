package dev.dache.app.boards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dache.core.boards.Board
import dev.dache.core.boards.BoardRepository
import dev.dache.core.boards.Widget
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The active profile's dashboards and every change to them; each change is saved as it's made. */
class BoardsViewModel(
    private val repository: BoardRepository,
) : ViewModel() {
    /** Null until the profiles have loaded, so an open board's page isn't closed before its board is known. */
    val boards: StateFlow<List<Board>?> = repository.boards

    /** Creates a board named [name] and hands it to [onCreated], e.g. to open it. */
    fun create(
        name: String,
        onCreated: (Board) -> Unit,
    ) {
        viewModelScope.launch { onCreated(repository.create(name)) }
    }

    fun rename(
        id: String,
        name: String,
    ) {
        viewModelScope.launch { repository.rename(id, name) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    /** Adds [widget], or replaces the one with its id. */
    fun putWidget(
        boardId: String,
        widget: Widget,
    ) = change(boardId) { it.withWidget(widget) }

    fun removeWidget(
        boardId: String,
        widgetId: String,
    ) = change(boardId) { it.withoutWidget(widgetId) }

    fun moveWidget(
        boardId: String,
        widgetId: String,
        by: Int,
    ) = change(boardId) { it.moved(widgetId, by) }

    fun newWidgetId(): String = repository.newWidgetId()

    /** [BoardActions] for board [boardId]; [onDeleted] runs once it's deleted, e.g. to leave its page. */
    fun actionsFor(
        boardId: String,
        onDeleted: () -> Unit,
    ): BoardActions =
        object : BoardActions {
            override fun rename(name: String) = this@BoardsViewModel.rename(boardId, name)

            override fun delete() {
                this@BoardsViewModel.delete(boardId)
                onDeleted()
            }

            override fun putWidget(widget: Widget) = this@BoardsViewModel.putWidget(boardId, widget)

            override fun removeWidget(id: String) = this@BoardsViewModel.removeWidget(boardId, id)

            override fun moveWidget(
                id: String,
                by: Int,
            ) = this@BoardsViewModel.moveWidget(boardId, id, by)

            override fun newWidgetId(): String = this@BoardsViewModel.newWidgetId()
        }

    private fun change(
        boardId: String,
        transform: (Board) -> Board,
    ) {
        viewModelScope.launch { repository.update(boardId, transform) }
    }
}
