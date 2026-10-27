package dev.dache.core.linking

import dev.dache.core.model.LinkReason
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.ResourceLink
import dev.dache.core.model.ResourceRef
import dev.dache.core.model.Site

/**
 * Computes cross-resource links locally from fetched data. Pure and deterministic.
 *
 * Rules:
 * - DNS record → site when the record name or target is one of the site's hostnames.
 * - DNS A/AAAA record → server when the record content is the server's public IP.
 * - Domain → site when a site hostname sits under the domain.
 * - Monitor → site when the monitored URL's host is one of the site's hostnames.
 * - Site → CI run when both point at the same repository.
 */
object ResourceLinker {
    fun link(snapshots: Collection<ProviderSnapshot>): List<ResourceLink> {
        val links = LinkSet()
        val siteHosts = snapshots.flatMap { it.sites }.associate { it.ref to it.hostnames() }
        linkDnsToSites(snapshots, siteHosts, links)
        linkDnsToServers(snapshots, links)
        linkDomainsToSites(snapshots, siteHosts, links)
        linkMonitorsToSites(snapshots, siteHosts, links)
        linkSitesToCiRuns(snapshots, links)
        return links.toList()
    }

    private fun linkDnsToSites(
        snapshots: Collection<ProviderSnapshot>,
        siteHosts: SiteHosts,
        links: LinkSet,
    ) {
        for (record in snapshots.flatMap { it.dnsRecords }) {
            val recordHosts = setOfNotNull(normalizeHost(record.name), normalizeHost(record.content))
            siteHosts.forEach { (site, hosts) ->
                if (recordHosts.any { it in hosts }) links.add(record.ref, site, LinkReason.HOSTNAME)
            }
        }
    }

    private fun linkDnsToServers(
        snapshots: Collection<ProviderSnapshot>,
        links: LinkSet,
    ) {
        val serversByIp = snapshots.flatMap { it.servers }.filter { it.publicIpv4 != null }.groupBy { it.publicIpv4 }
        for (record in snapshots.flatMap { it.dnsRecords }.filter { it.type == "A" || it.type == "AAAA" }) {
            serversByIp[record.content.trim()].orEmpty().forEach {
                links.add(
                    record.ref,
                    it.ref,
                    LinkReason.IP_ADDRESS,
                )
            }
        }
    }

    private fun linkDomainsToSites(
        snapshots: Collection<ProviderSnapshot>,
        siteHosts: SiteHosts,
        links: LinkSet,
    ) {
        for (domain in snapshots.flatMap { it.domains }) {
            val name = normalizeHost(domain.name) ?: continue
            siteHosts.forEach { (site, hosts) ->
                if (hosts.any { isWithinDomain(it, name) }) links.add(domain.ref, site, LinkReason.HOSTNAME)
            }
        }
    }

    private fun linkMonitorsToSites(
        snapshots: Collection<ProviderSnapshot>,
        siteHosts: SiteHosts,
        links: LinkSet,
    ) {
        for (monitor in snapshots.flatMap { it.monitors }) {
            val host = normalizeHost(monitor.url) ?: continue
            siteHosts.forEach { (site, hosts) ->
                if (host in hosts) links.add(monitor.ref, site, LinkReason.HOSTNAME)
            }
        }
    }

    private fun linkSitesToCiRuns(
        snapshots: Collection<ProviderSnapshot>,
        links: LinkSet,
    ) {
        val sitesByRepo =
            snapshots
                .flatMap { it.sites }
                .filter { it.repoUrl != null }
                .groupBy { it.repoUrl?.let(::normalizeRepoUrl) }
        for (run in snapshots.flatMap { it.ciRuns }) {
            sitesByRepo[
                normalizeRepoUrl(
                    run.repoUrl,
                ),
            ].orEmpty().forEach { links.add(it.ref, run.ref, LinkReason.REPO_URL) }
        }
    }

    private fun Site.hostnames(): Set<String> = (customDomains + primaryUrl).mapNotNull(::normalizeHost).toSet()
}

private typealias SiteHosts = Map<ResourceRef, Set<String>>

/** Undirected links stored in a canonical order so duplicates collapse. */
private class LinkSet {
    private val links = LinkedHashSet<ResourceLink>()

    fun add(
        a: ResourceRef,
        b: ResourceRef,
        reason: LinkReason,
    ) {
        if (a == b) return
        val (first, second) = if (a.toString() <= b.toString()) a to b else b to a
        links += ResourceLink(first, second, reason)
    }

    fun toList(): List<ResourceLink> = links.toList()
}
