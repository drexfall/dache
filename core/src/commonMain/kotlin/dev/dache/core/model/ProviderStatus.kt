package dev.dache.core.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** How a provider's own service is doing, worst last. */
enum class StatusLevel {
    OPERATIONAL,
    MAINTENANCE,
    DEGRADED,
    PARTIAL_OUTAGE,
    MAJOR_OUTAGE,
    UNKNOWN,
    ;

    /** Anything short of fully working, maintenance included. */
    val isImpaired: Boolean get() = this != OPERATIONAL && this != UNKNOWN
}

/** One part of the provider's service. [group] names the group it sits in, such as a region. */
@Serializable
data class StatusComponent(
    val name: String,
    val level: StatusLevel,
    val group: String?,
)

@Serializable
data class StatusIncident(
    val name: String,
    val status: String?,
    val impact: StatusLevel,
    val updatedAt: Instant?,
    val url: String?,
)

/** The provider's public status page, as of [fetchedAt]. */
@Serializable
data class ProviderStatus(
    val providerId: ProviderId,
    val level: StatusLevel,
    val description: String?,
    val pageUrl: String?,
    val components: List<StatusComponent>,
    val incidents: List<StatusIncident>,
    val fetchedAt: Instant,
)
