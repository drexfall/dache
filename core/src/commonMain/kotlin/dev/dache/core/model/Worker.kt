package dev.dache.core.model

import kotlin.time.Instant

/** A serverless script, such as a Cloudflare Worker. */
data class Worker(
    override val providerId: ProviderId,
    override val accountId: String,
    override val nativeId: String,
    override val webUrl: String,
    val name: String,
    /** Route patterns the script answers on: a hostname, usually with a path wildcard. */
    val routes: List<String>,
    val modifiedAt: Instant?,
) : Resource {
    override val kind: ResourceKind get() = ResourceKind.WORKER
    override val displayName: String get() = name
}
