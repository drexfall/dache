package dev.dache.core.model

/** Fields shared by every unified model. */
interface Resource {
    val providerId: ProviderId
    val accountId: String
    val nativeId: String
    val webUrl: String
    val kind: ResourceKind

    /** Human-readable name for lists, chips and links. */
    val displayName: String

    val ref: ResourceRef get() = ResourceRef(providerId, accountId, nativeId, kind)
}

enum class ResourceKind {
    SITE,
    DOMAIN,
    DNS_RECORD,
    CI_RUN,
    SERVER,
    WORKER,
    MONITOR,

    /** A [ServiceResource]: anything a plugin describes itself, such as an R2 bucket. */
    SERVICE,
}

/** Stable pointer to a resource, used as the endpoint of a [ResourceLink]. */
data class ResourceRef(
    val providerId: ProviderId,
    val accountId: String,
    val nativeId: String,
    val kind: ResourceKind,
)
