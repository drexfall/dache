package dev.dache.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Monitor(
    override val providerId: ProviderId,
    override val accountId: String,
    override val nativeId: String,
    override val webUrl: String,
    val name: String,
    val url: String,
    val status: MonitorStatus,
    val uptimePercent: Double?,
    val responseTimeMs: Int?,
    /** Response time samples in milliseconds, oldest first. */
    val responseTimes: List<TimedValue> = emptyList(),
    /** Uptime percentage per UTC day, oldest first. */
    val dailyUptime: List<TimedValue> = emptyList(),
) : Resource {
    override val kind: ResourceKind get() = ResourceKind.MONITOR
    override val displayName: String get() = name
}

enum class MonitorStatus {
    UP,
    DOWN,
    PAUSED,
    UNKNOWN,
}
