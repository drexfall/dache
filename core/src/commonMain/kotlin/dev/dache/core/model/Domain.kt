package dev.dache.core.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** A domain or DNS zone. [expiresAt] is null when the provider is not the registrar. */
@Serializable
data class Domain(
    override val providerId: ProviderId,
    override val accountId: String,
    override val nativeId: String,
    override val webUrl: String,
    val name: String,
    val expiresAt: Instant?,
    /** TLS certificates the provider manages for the domain, empty when it doesn't report them. */
    val certificates: List<Certificate> = emptyList(),
) : Resource {
    override val kind: ResourceKind get() = ResourceKind.DOMAIN
    override val displayName: String get() = name
}

/** A TLS certificate covering [hosts]. [expiresAt] is null until one is issued. */
@Serializable
data class Certificate(
    val nativeId: String,
    val status: CertificateStatus,
    val hosts: List<String>,
    val expiresAt: Instant?,
)

enum class CertificateStatus {
    ACTIVE,
    PENDING,
    EXPIRED,
    FAILED,

    /** Deleted or switched off; it isn't serving and isn't meant to. */
    INACTIVE,
    UNKNOWN,
}

@Serializable
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
