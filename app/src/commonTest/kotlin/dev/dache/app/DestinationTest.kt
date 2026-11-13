package dev.dache.app

import androidx.compose.runtime.saveable.SaverScope
import dev.dache.app.navigation.BackStackSaver
import dev.dache.app.navigation.Destination
import dev.dache.app.navigation.accountId
import dev.dache.app.navigation.current
import dev.dache.app.navigation.keepingAccounts
import dev.dache.app.navigation.pop
import dev.dache.app.navigation.push
import dev.dache.app.navigation.sidebarEntry
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
    private val start = listOf<Destination>(Destination.Overview)

    @Test
    fun openingPagesPushesThemAndBackPopsThem() {
        val vercel = Destination.Account("vercel-acc")

        val opened = start.push(vercel).push(record).push(worker)

        assertEquals(listOf(Destination.Overview, vercel, record, worker), opened)
        assertEquals(worker, opened.current)
        assertEquals(listOf(Destination.Overview, vercel, record), opened.pop())
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
        assertEquals(start, atRecord.push(Destination.Overview))
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

        assertEquals(listOf(Destination.Overview, account, settings), stack)
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

        assertEquals(listOf(Destination.Overview, Destination.Plugins), stack.keepingAccounts(listOf("vercel-acc")))
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
                .push(Destination.Plugins)
        val scope = SaverScope { true }

        val saved = with(BackStackSaver) { scope.save(stack) }

        assertEquals(stack, saved?.let { BackStackSaver.restore(it) })
    }

    @Test
    fun saverFallsBackToOverviewOnBadInput() {
        assertEquals(start, BackStackSaver.restore(listOf("account")))
        assertEquals(start, BackStackSaver.restore(listOf("overview", "nonsense")))
        assertEquals(start, BackStackSaver.restore(emptyList()))
    }
}
