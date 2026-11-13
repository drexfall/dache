package dev.dache.core.model

import kotlinx.serialization.Serializable

/** Everything one account returned in a single refresh. */
@Serializable
data class ProviderSnapshot(
    val sites: List<Site> = emptyList(),
    val domains: List<Domain> = emptyList(),
    val dnsRecords: List<DnsRecord> = emptyList(),
    val ciRuns: List<CiRun> = emptyList(),
    val servers: List<Server> = emptyList(),
    val workers: List<Worker> = emptyList(),
    val monitors: List<Monitor> = emptyList(),
    val services: List<ServiceResource> = emptyList(),
    val billing: Billing? = null,
    /** Permissions the token lacked for optional resources, which were left out rather than failing the refresh. */
    val missingPermissions: Set<String> = emptySet(),
    /** Permissions of optional resources that failed to load for another reason, e.g. a server error. */
    val unavailable: Set<String> = emptySet(),
) {
    val resources: List<Resource>
        get() = sites + domains + dnsRecords + ciRuns + servers + workers + monitors + services

    /**
     * The same snapshot naming [provider] on every resource. A snapshot read back from disk knows its provider by
     * id alone; this gives it the installed plugin's display name and links again.
     */
    fun withProvider(provider: ProviderId): ProviderSnapshot =
        copy(
            sites = sites.map { it.copy(providerId = provider) },
            domains = domains.map { it.copy(providerId = provider) },
            dnsRecords = dnsRecords.map { it.copy(providerId = provider) },
            ciRuns = ciRuns.map { it.copy(providerId = provider) },
            servers = servers.map { it.copy(providerId = provider) },
            workers = workers.map { it.copy(providerId = provider) },
            monitors = monitors.map { it.copy(providerId = provider) },
            services = services.map { it.copy(providerId = provider) },
        )

    /** Only what [page] shows. Billing belongs to the whole account, so it is left out. */
    fun only(page: ServicePage): ProviderSnapshot =
        ProviderSnapshot(
            sites = sites.filter(page::shows),
            domains = domains.filter(page::shows),
            dnsRecords = dnsRecords.filter(page::shows),
            ciRuns = ciRuns.filter(page::shows),
            servers = servers.filter(page::shows),
            workers = workers.filter(page::shows),
            monitors = monitors.filter(page::shows),
            services = services.filter(page::shows),
            missingPermissions = missingPermissions intersect page.permissions,
            unavailable = unavailable intersect page.permissions,
        )
}
