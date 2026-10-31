package dev.dache.core.model

import kotlin.time.Instant

/**
 * A resource with no model of its own, described entirely by its plugin: an R2 bucket, a KV namespace, a tunnel.
 * [service] is the plugin's id for what it is, unique within the plugin; [serviceLabel] is its plural name for
 * them, e.g. "R2 buckets". [nativeId] is prefixed with [service], so ids from different services can't collide.
 */
data class ServiceResource(
    override val providerId: ProviderId,
    override val accountId: String,
    override val nativeId: String,
    override val webUrl: String,
    val service: String,
    val serviceLabel: String,
    val name: String,
    /** One line about it, such as a bucket's region. */
    val detail: String?,
    /** Null for things that have no health, like a bucket. */
    val status: ServiceStatus?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
) : Resource {
    override val kind: ResourceKind get() = ResourceKind.SERVICE
    override val displayName: String get() = name
}

enum class ServiceStatus {
    HEALTHY,
    DEGRADED,
    DOWN,
    INACTIVE,
    UNKNOWN,
}
