package dev.dache.app.boards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dache.app.resources.Res
import dev.dache.app.resources.board_default_name
import dev.dache.core.boards.Board
import dev.dache.core.boards.BoardIcon
import dev.dache.core.boards.BoardRepository
import dev.dache.core.boards.Widget
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/** The active profile's boards and every change to them; each change is saved as it's made. */
class BoardsViewModel(
    private val repository: BoardRepository,
) : ViewModel() {
    /** Null until the profiles have loaded, so an open board's page isn't closed before its board is known. */
    val boards: StateFlow<List<Board>?> = repository.boards

    /**
     * Creates a board named "Board 1", "Board 2" and so on, taking the first number no board of the profile is
     * named with, and hands it to [onCreated], e.g. to open it.
     */
    fun create(onCreated: (Board) -> Unit) {
        viewModelScope.launch {
            val taken = boards.value.orEmpty().map { it.name }
            val name = firstFreeName(taken) { getString(Res.string.board_default_name, it) }
            onCreated(repository.create(name))
        }
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

/** The first of [nameFor] 1, 2, 3… not among [taken]. Inline so [nameFor] can suspend, to look up a string. */
inline fun firstFreeName(
    taken: Collection<String>,
    nameFor: (Int) -> String,
): String {
    val used = taken.map { it.trim() }.toSet()
    var number = 1
    while (true) {
        val name = nameFor(number)
        if (name !in used) return name
        number++
    }
}
