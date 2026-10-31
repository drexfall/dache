package dev.dache.core.model

/** What a provider can show. Write capabilities are reserved for Phase 3 and not implemented. */
enum class Capability {
    SITES,
    DEPLOYMENTS,
    DOMAINS,
    DNS,
    CI,
    SERVERS,
    WORKERS,
    MONITORS,

    /** [ServiceResource]s: storage, queues, tunnels and anything else a plugin describes itself. */
    SERVICES,
}
