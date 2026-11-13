package dev.dache.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Server(
    override val providerId: ProviderId,
    override val accountId: String,
    override val nativeId: String,
    override val webUrl: String,
    val name: String,
    val region: String,
    val status: ServerStatus,
    val publicIpv4: String?,
) : Resource {
    override val kind: ResourceKind get() = ResourceKind.SERVER
    override val displayName: String get() = name
}

enum class ServerStatus {
    ACTIVE,
    OFF,
    PROVISIONING,
    UNKNOWN,
}
