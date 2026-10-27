package dev.dache.core.model

import kotlin.time.Instant

/** A domain or DNS zone. [expiresAt] is null when the provider is not the registrar. */
data class Domain(
    override val providerId: ProviderId,
    override val accountId: String,
    override val nativeId: String,
    override val webUrl: String,
    val name: String,
    val expiresAt: Instant?,
) : Resource {
    override val kind: ResourceKind get() = ResourceKind.DOMAIN
    override val displayName: String get() = name
}

data class DnsRecord(
    override val providerId: ProviderId,
    override val accountId: String,
    override val nativeId: String,
    override val webUrl: String,
    val zoneName: String,
    val type: String,
    val name: String,
    val content: String,
) : Resource {
    override val kind: ResourceKind get() = ResourceKind.DNS_RECORD
    override val displayName: String get() = "$type $name"
}
