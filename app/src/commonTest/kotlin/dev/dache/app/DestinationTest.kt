package dev.dache.app

import androidx.compose.runtime.saveable.SaverScope
import dev.dache.app.navigation.Destination
import dev.dache.app.navigation.DestinationSaver
import dev.dache.app.navigation.accountId
import dev.dache.app.navigation.openResource
import dev.dache.app.navigation.sidebarEntry
import dev.dache.app.navigation.trail
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ResourceKind
import dev.dache.core.model.ResourceRef
import kotlin.test.Test
import kotlin.test.assertEquals

class DestinationTest {
    private val site = ResourceRef(ProviderId.VERCEL, "vercel-acc", "site-1", ResourceKind.SITE)
    private val record = ResourceRef(ProviderId.CLOUDFLARE, "cf-acc", "zone:rec/1", ResourceKind.DNS_RECORD)
    private val worker = ResourceRef(ProviderId.CLOUDFLARE, "cf-acc", "worker-1", ResourceKind.WORKER)

    @Test
    fun openingResourcesExtendsTheTrail() {
        val start = Destination.Account("vercel-acc")

        val opened = start.openResource(record).openResource(worker)

        assertEquals(
            listOf(start, Destination.Resource(record, start), opened),
            opened.trail,
        )
    }

    @Test
    fun openingAResourceAlreadyOnTheTrailGoesBackToIt() {
        val atRecord = Destination.Overview.openResource(site).openResource(record)

        val back = atRecord.openResource(worker).openResource(record)

        assertEquals(atRecord, back)
    }

    @Test
    fun resourcePagesHighlightTheirOwnAccount() {
        val opened = Destination.Account("vercel-acc").openResource(record)

        assertEquals(Destination.Account("cf-acc"), opened.sidebarEntry)
    }

    @Test
    fun saverRoundTripsTheWholeTrail() {
        val opened = Destination.Account("vercel-acc").openResource(record).openResource(worker)
        val scope = SaverScope { true }

        val saved = with(DestinationSaver) { scope.save(opened) }

        assertEquals(opened, saved?.let { DestinationSaver.restore(it) })
    }

    @Test
    fun anAccountsOwnPagesSitUnderIt() {
        val dns = Destination.Service("cf-acc", "dns")
        val settings = Destination.AccountSettings("cf-acc")

        assertEquals(listOf(Destination.Account("cf-acc"), dns), dns.trail)
        assertEquals(listOf(Destination.Account("cf-acc"), settings), settings.trail)
        assertEquals(dns, dns.sidebarEntry)
        assertEquals(Destination.Account("cf-acc"), settings.sidebarEntry)
    }

    @Test
    fun resourcesOpenedFromAServicePageKeepItOnTheTrail() {
        val dns = Destination.Service("cf-acc", "dns")

        val opened = dns.openResource(record)

        assertEquals(listOf(Destination.Account("cf-acc"), dns, opened), opened.trail)
        assertEquals("cf-acc", opened.accountId)
    }

    @Test
    fun saverRoundTripsServiceAndSettingsPages() {
        val scope = SaverScope { true }
        listOf(
            Destination.Service("cf-acc", "r2").openResource(worker),
            Destination.AccountSettings("cf-acc"),
            Destination.Service("cf-acc", "dns"),
        ).forEach { destination ->
            val saved = with(DestinationSaver) { scope.save(destination) }

            assertEquals(destination, saved?.let { DestinationSaver.restore(it) })
        }
    }

    @Test
    fun saverFallsBackToOverviewOnBadInput() {
        assertEquals(Destination.Overview, DestinationSaver.restore(listOf("account")))
    }
}
