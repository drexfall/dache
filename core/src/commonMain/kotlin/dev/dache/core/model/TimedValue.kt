package dev.dache.core.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** One point of a time series. */
@Serializable
data class TimedValue(
    val at: Instant,
    val value: Double,
)
