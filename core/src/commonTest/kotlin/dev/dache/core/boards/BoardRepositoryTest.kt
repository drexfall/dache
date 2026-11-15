package dev.dache.core.boards

import dev.dache.core.account.InMemoryProfileStore
import dev.dache.core.model.Profile
import dev.dache.core.model.Profiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class BoardRepositoryTest {
    private val testScope = TestScope()
    private val io = UnconfinedTestDispatcher(testScope.testScheduler)
    private val fileSystem = FakeFileSystem()
    private val store = FileBoardStore(fileSystem, "/data/boards".toPath(), io)
    private val work = Profile("work", "Work")
    private val profileStore = InMemoryProfileStore(Profiles(listOf(Profile.DEFAULT, work)))
    private var ids = 0

    private fun TestScope.repository() =
        BoardRepository(
            store,
            profileStore.profiles,
            CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            newId = { "id-${ids++}" },
        )

    @Test
    fun boardsBelongToTheActiveProfileAndSurviveARestart() =
        testScope.runTest {
            val boards = repository()
            val ops = boards.create("  Ops  ")
            boards.update(ops.id) { it.withWidget(Widget(boards.newWidgetId(), Datapoint.DEPLOYS, span = 2)) }
            profileStore.updateProfiles { it.copy(activeId = work.id) }
            boards.create("Work board")

            assertEquals(
                listOf("Work board"),
                boards.boards.value
                    .orEmpty()
                    .map { it.name },
            )

            profileStore.updateProfiles { it.copy(activeId = Profile.DEFAULT_ID) }
            val reloaded = repository()
            val board =
                reloaded.boards.value
                    .orEmpty()
                    .single()
            assertEquals("Ops", board.name)
            assertEquals(Datapoint.DEPLOYS, board.widgets.single().datapoint)
            assertEquals(2, board.widgets.single().span)
        }

    @Test
    fun deletingAProfileDeletesItsBoards() =
        testScope.runTest {
            val boards = repository()
            profileStore.updateProfiles { it.copy(activeId = work.id) }
            val board = boards.create("Work board")

            profileStore.updateProfiles { Profiles(listOf(Profile.DEFAULT)) }

            assertNull(boards.board(board.id))
            assertEquals(emptyList(), store.load())
        }

    @Test
    fun widgetsMoveAndAreRemoved() =
        testScope.runTest {
            val boards = repository()
            val board = boards.create("B")
            listOf(Datapoint.DEPLOYS, Datapoint.UPTIME, Datapoint.SITES).forEachIndexed { i, datapoint ->
                boards.update(board.id) { it.withWidget(Widget("w$i", datapoint)) }
            }

            fun order() = boards.board(board.id)!!.widgets.map { it.id }

            boards.update(board.id) { it.moved("w0", 1) }
            assertEquals(listOf("w1", "w0", "w2"), order())
            boards.update(board.id) { it.moved("w2", -5) }
            assertEquals(listOf("w2", "w1", "w0"), order())
            boards.update(board.id) { it.movedTo("w2", 2) }
            assertEquals(listOf("w1", "w0", "w2"), order())
            boards.update(board.id) { it.withoutWidget("w0") }
            assertEquals(listOf("w1", "w2"), order())

            boards.delete(board.id)
            assertEquals(emptyList(), boards.boards.value.orEmpty())
        }
}
