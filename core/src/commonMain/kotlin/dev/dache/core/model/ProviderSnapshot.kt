package dev.dache.core.model

/** Everything one account returned in a single refresh. */
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
) {
    val resources: List<Resource>
        get() = sites + domains + dnsRecords + ciRuns + servers + workers + monitors + services

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
        )
}
