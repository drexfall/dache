package dev.dache.core.boards

import dev.dache.core.dashboard.JsonDirectory
import dev.dache.core.model.Profiles
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import okio.Path
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Where boards are kept. */
interface BoardStore {
    fun load(): List<Board>

    suspend fun save(board: Board)

    suspend fun delete(id: String)

    /** Keeps boards in memory only: for tests and previews. */
    class InMemory(
        initial: List<Board> = emptyList(),
    ) : BoardStore {
        private val boards = initial.associateBy { it.id }.toMutableMap()

        override fun load(): List<Board> = boards.values.toList()

        override suspend fun save(board: Board) {
            boards[board.id] = board
        }

        override suspend fun delete(id: String) {
            boards.remove(id)
        }
    }
}

/** One JSON file per board under [dir]: `<boardId>.json`. Boards hold no token or account data, only choices. */
class FileBoardStore(
    fileSystem: FileSystem,
    dir: Path,
    io: CoroutineDispatcher = Dispatchers.IO,
) : BoardStore {
    private val files = JsonDirectory(fileSystem, dir, io)

    override fun load(): List<Board> = files.readAll(Board.serializer()).values.toList()

    override suspend fun save(board: Board) = files.write(board.id, Board.serializer(), board)

    override suspend fun delete(id: String) = files.keepOnly(load().map { it.id }.filter { it != id })
}

/**
 * The user's boards. [boards] are the active profile's, oldest first, and null until the profiles have loaded; a
 * board of a profile that is deleted goes with it. Every change is saved at once.
 */
class BoardRepository(
    private val store: BoardStore,
    private val profiles: Flow<Profiles>,
    scope: CoroutineScope,
    private val newId: () -> String = ::randomBoardId,
) {
    private val all = MutableStateFlow(store.load().map { it.copy(widgets = it.widgets.map(Widget::normalized)) })
    private val lock = Mutex()

    val boards: StateFlow<List<Board>?> =
        combine(all, profiles) { boards, profiles -> boards.filter { it.profileId == profiles.active.id } }
            .stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            profiles.collect { current ->
                val known = current.all.map { it.id }.toSet()
                all.value.filter { it.profileId !in known }.forEach { delete(it.id) }
            }
        }
    }

    fun board(id: String): Board? = all.value.firstOrNull { it.id == id }

    /** A new empty board in the active profile. */
    suspend fun create(name: String): Board {
        val board = Board(newId(), name.cleanName(), profiles.first().active.id)
        save(board)
        return board
    }

    suspend fun rename(
        id: String,
        name: String,
    ) = update(id) { it.copy(name = name.cleanName()) }

    /** Applies [change] to board [id] and saves it; nothing happens if the board is gone. */
    suspend fun update(
        id: String,
        change: (Board) -> Board,
    ) = lock.withLock {
        val board = all.value.firstOrNull { it.id == id } ?: return@withLock
        val changed = change(board).copy(id = board.id, profileId = board.profileId)
        all.update { boards -> boards.map { if (it.id == id) changed else it } }
        store.save(changed)
    }

    suspend fun delete(id: String) =
        lock.withLock {
            all.update { boards -> boards.filterNot { it.id == id } }
            store.delete(id)
        }

    /** An id for a new widget. */
    fun newWidgetId(): String = newId()

    private suspend fun save(board: Board) =
        lock.withLock {
            all.update { it + board }
            store.save(board)
        }

    private fun String.cleanName(): String = trim().take(Board.MAX_NAME_LENGTH)
}

@OptIn(ExperimentalUuidApi::class)
private fun randomBoardId(): String = Uuid.random().toString()
