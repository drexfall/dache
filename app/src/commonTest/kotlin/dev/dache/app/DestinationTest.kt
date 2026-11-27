package dev.dache.app

import androidx.compose.runtime.saveable.SaverScope
import dev.dache.app.navigation.BackStackSaver
import dev.dache.app.navigation.Destination
import dev.dache.app.navigation.accountId
import dev.dache.app.navigation.current
import dev.dache.app.navigation.keepingAccounts
import dev.dache.app.navigation.keepingBoardIds
import dev.dache.app.navigation.keepingBoards
import dev.dache.app.navigation.pop
import dev.dache.app.navigation.push
import dev.dache.app.navigation.sidebarEntry
import dev.dache.core.boards.Board
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ResourceKind
import dev.dache.core.model.ResourceRef
import kotlin.test.Test
import kotlin.test.assertEquals

class DestinationTest {
    private val site = Destination.Resource(ResourceRef(ProviderId.VERCEL, "vercel-acc", "site-1", ResourceKind.SITE))
    private val record =
        Destination.Resource(ResourceRef(ProviderId.CLOUDFLARE, "cf-acc", "zone:rec/1", ResourceKind.DNS_RECORD))
    private val worker =
        Destination.Resource(
            ResourceRef(ProviderId.CLOUDFLARE, "cf-acc", "worker-1", ResourceKind.WORKER),
        )
    private val start = listOf<Destination>(Destination.Home)

    @Test
    fun openingPagesPushesThemAndBackPopsThem() {
        val vercel = Destination.Account("vercel-acc")

        val opened = start.push(vercel).push(record).push(worker)

        assertEquals(listOf(Destination.Home, vercel, record, worker), opened)
        assertEquals(worker, opened.current)
        assertEquals(listOf(Destination.Home, vercel, record), opened.pop())
    }

    @Test
    fun theFirstPageStaysOnBack() {
        assertEquals(start, start.pop())
    }

    @Test
    fun openingAPageAlreadyOpenGoesBackToIt() {
        val atRecord = start.push(site).push(record)

        assertEquals(atRecord, atRecord.push(worker).push(record))
        assertEquals(atRecord, atRecord.push(record))
        assertEquals(start, atRecord.push(Destination.Home))
    }

    @Test
    fun anAccountsPagesReplaceEachOtherOnTop() {
        val account = Destination.Account("cf-acc")
        val dns = Destination.Service("cf-acc", "dns")
        val workers = Destination.Service("cf-acc", "workers")
        val settings = Destination.AccountSettings("cf-acc")

        val stack =
            start
                .push(account)
                .push(dns)
                .push(workers)
                .push(settings)

        assertEquals(listOf(Destination.Home, account, settings), stack)
        // Another account's page is a new step, not a sibling.
        assertEquals(4, stack.push(Destination.Service("do-acc", "dns")).size)
    }

    @Test
    fun removingAnAccountDropsItsPages() {
        val cf = Destination.Account("cf-acc")
        val stack =
            start
                .push(cf)
                .push(Destination.Service("cf-acc", "dns"))
                .push(record)
                .push(Destination.Plugins)

        assertEquals(listOf(Destination.Home, Destination.Plugins), stack.keepingAccounts(listOf("vercel-acc")))
        assertEquals(stack, stack.keepingAccounts(listOf("cf-acc")))
        assertEquals(start, listOf<Destination>(cf).keepingAccounts(emptyList()))
    }

    @Test
    fun resourcePagesHighlightTheirOwnAccount() {
        assertEquals(Destination.Account("cf-acc"), record.sidebarEntry)
        assertEquals(Destination.Account("cf-acc"), Destination.AccountSettings("cf-acc").sidebarEntry)
        assertEquals(Destination.Service("cf-acc", "dns"), Destination.Service("cf-acc", "dns").sidebarEntry)
        assertEquals("cf-acc", record.accountId)
    }

    @Test
    fun saverRoundTripsTheWholeStack() {
        val stack =
            start
                .push(Destination.ManageAccounts)
                .push(Destination.Account("cf-acc"))
                .push(Destination.Service("cf-acc", "r2"))
                .push(worker)
                .push(Destination.AccountSettings("cf-acc"))
                .push(Destination.Board("board-1"))
                .push(Destination.BoardEditor("board-1", widgetId = "w2"))
                .push(Destination.BoardEditor("board-1"))
                .push(Destination.Plugins)
        val scope = SaverScope { true }

        val saved = with(BackStackSaver) { scope.save(stack) }

        assertEquals(stack, saved?.let { BackStackSaver.restore(it) })
    }

    @Test
    fun aDeletedOrOtherProfilesBoardLosesItsPage() {
        val home = Destination.Board("home")
        val board = Destination.Board("b1")
        val stack = listOf(home, board, Destination.BoardEditor("b1", "w1"), Destination.Plugins)
        val boards = listOf(board("home"), board("b1"))

        assertEquals(stack, stack.keepingBoards(null), "boards not loaded yet: keep everything")
        assertEquals(stack, stack.keepingBoards(emptyList()), "no boards yet: keep everything")
        assertEquals(stack, stack.keepingBoards(boards))
        assertEquals(listOf(home, Destination.Plugins), stack.keepingBoards(boards.take(1)))
        assertEquals(listOf(Destination.Home), listOf<Destination>(board).keepingBoardIds(emptyList()))
        assertEquals(board, Destination.BoardEditor("b1").sidebarEntry, "the editor highlights its board")
    }

    @Test
    fun homeBecomesTheFirstBoardOnceBoardsLoad() {
        val home = Destination.Board("home")
        val boards = listOf(board("home"), board("b1"))

        assertEquals(listOf(home), start.keepingBoards(boards))
        assertEquals(listOf(home, Destination.Plugins), start.push(Destination.Plugins).keepingBoards(boards))
        assertEquals(listOf(home), listOf(Destination.Board("gone")).keepingBoards(boards), "nothing left: home")
    }

    @Test
    fun saverFallsBackToHomeOnBadInput() {
        assertEquals(start, BackStackSaver.restore(listOf("account")))
        assertEquals(start, BackStackSaver.restore(listOf("home", "nonsense")))
        assertEquals(start, BackStackSaver.restore(emptyList()))
        assertEquals(start, BackStackSaver.restore(listOf("overview")), "a stack saved before boards opens home")
    }

    private fun board(id: String) = Board(id, id, "default")
}
