package dev.dache.core.model

/** An undirected relation between two resources, possibly on different providers. */
data class ResourceLink(
    val a: ResourceRef,
    val b: ResourceRef,
    val reason: LinkReason,
) {
    fun other(ref: ResourceRef): ResourceRef? =
        when (ref) {
            a -> b
            b -> a
            else -> null
        }
}

enum class LinkReason {
    /** DNS record or monitor points at a site's hostname, or a hostname sits under a domain. */
    HOSTNAME,

    /** Site builds from the same repository a CI run belongs to. */
    REPO_URL,

    /** DNS A/AAAA record points at a server's public IP. */
    IP_ADDRESS,
}
