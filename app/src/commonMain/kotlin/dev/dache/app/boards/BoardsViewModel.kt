package dev.dache.app.boards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dache.core.boards.Board
import dev.dache.core.boards.BoardIcon
import dev.dache.core.boards.BoardRepository
import dev.dache.core.boards.Widget
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The active profile's boards and every change to them; each change is saved as it's made. */
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

    fun setIcon(
        id: String,
        icon: BoardIcon,
    ) = change(id) { it.copy(icon = icon) }

    /** Deletes board [id], unless it's the profile's last, and then runs [onDeleted]. */
    fun delete(
        id: String,
        onDeleted: () -> Unit = {},
    ) {
        viewModelScope.launch { if (repository.delete(id)) onDeleted() }
    }

    /** Saves [widgets] as board [boardId]'s, in this order; the editor calls this after every change. */
    fun saveWidgets(
        boardId: String,
        widgets: List<Widget>,
    ) = change(boardId) { it.withWidgets(widgets) }

    fun newWidgetId(): String = repository.newWidgetId()

    /** [BoardActions] for board [boardId]; [onDeleted] runs once it's deleted, e.g. to leave its page. */
    fun actionsFor(
        boardId: String,
        onDeleted: () -> Unit,
    ): BoardActions =
        object : BoardActions {
            override fun rename(name: String) = this@BoardsViewModel.rename(boardId, name)

            override fun setIcon(icon: BoardIcon) = this@BoardsViewModel.setIcon(boardId, icon)

            override fun delete() = this@BoardsViewModel.delete(boardId, onDeleted)

            override fun saveWidgets(widgets: List<Widget>) = this@BoardsViewModel.saveWidgets(boardId, widgets)

            override fun newWidgetId(): String = this@BoardsViewModel.newWidgetId()
        }

    private fun change(
        boardId: String,
        transform: (Board) -> Board,
    ) {
        viewModelScope.launch { repository.update(boardId, transform) }
    }
}
