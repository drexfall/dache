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
    val billing: Billing? = null,
) {
    val resources: List<Resource> get() = sites + domains + dnsRecords + ciRuns + servers + workers + monitors
}
