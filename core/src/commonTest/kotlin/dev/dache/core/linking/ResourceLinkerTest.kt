package dev.dache.core.linking

import dev.dache.core.model.LinkReason
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ResourceKind
import dev.dache.core.model.ResourceLink
import dev.dache.core.provider.mock.MockFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ResourceLinkerTest {
    private val now = Instant.parse("2026-10-27T12:00:00Z")
    private val snapshots = ProviderId.entries.map { MockFixtures.snapshot(it, "acc-${it.id}", now) }
    private val links = ResourceLinker.link(snapshots)

    private fun linked(
        aId: String,
        bId: String,
        reason: LinkReason,
    ): Boolean =
        links.any {
            setOf(it.a.nativeId, it.b.nativeId) == setOf(aId, bId) && it.reason == reason
        }

    @Test
    fun cnameTargetLinksRecordToSite() {
        assertTrue(linked("rec-www", "nf-site-marketing", LinkReason.HOSTNAME))
        assertTrue(linked("rec-blog", "pages-blog", LinkReason.HOSTNAME))
    }

    @Test
    fun recordNameMatchingCustomDomainLinksToSite() {
        // app.acme.dev points at a generic Vercel target, so only the record name can match.
        assertTrue(linked("rec-app", "prj_app", LinkReason.HOSTNAME))
    }

    @Test
    fun aRecordLinksToServerByIp() {
        assertTrue(linked("rec-api", "41000001", LinkReason.IP_ADDRESS))
        assertTrue(linked("do-rec-1", "41000002", LinkReason.IP_ADDRESS))
    }

    @Test
    fun domainLinksToEverySiteUnderIt() {
        val sitesUnderAcme =
            links
                .filter { it.reason == LinkReason.HOSTNAME }
                .mapNotNull { link ->
                    listOf(link.a, link.b).takeIf { refs -> refs.any { it.nativeId == "zone-acme-dev" } }
                }.flatten()
                .filter { it.kind == ResourceKind.SITE }
                .map { it.nativeId }
                .toSet()
        assertEquals(setOf("nf-site-marketing", "nf-site-docs", "prj_app", "prj_status", "pages-blog"), sitesUnderAcme)
    }

    @Test
    fun monitorLinksToSiteByHost() {
        assertTrue(linked("m-780002", "nf-site-docs", LinkReason.HOSTNAME))
        assertTrue(linked("m-780003", "prj_app", LinkReason.HOSTNAME))
    }

    @Test
    fun repoUrlLinksSiteToCiRunAcrossUrlForms() {
        // Netlify docs site uses the git@ form; GitHub run uses https.
        assertTrue(linked("nf-site-docs", "9001", LinkReason.REPO_URL))
        assertTrue(linked("prj_app", "9002", LinkReason.REPO_URL))
    }

    @Test
    fun workerRouteLinksToRecordMonitorAndDomain() {
        assertTrue(linked("acme-edge", "rec-api", LinkReason.HOSTNAME))
        assertTrue(linked("acme-edge", "m-780004", LinkReason.HOSTNAME))
        assertTrue(linked("acme-edge", "zone-acme-dev", LinkReason.HOSTNAME))
        assertTrue(links.none { setOf(it.a.nativeId, it.b.nativeId) == setOf("acme-edge", "rec-www") })
    }

    @Test
    fun noDuplicatesOrSelfLinks() {
        assertEquals(links.size, links.toSet().size)
        assertTrue(links.none { it.a == it.b })
        val reversed = links.map { ResourceLink(it.b, it.a, it.reason) }.toSet()
        assertTrue(links.none { it in reversed })
    }

    @Test
    fun emptyInputGivesNoLinks() {
        assertEquals(emptyList(), ResourceLinker.link(emptyList()))
    }
}
