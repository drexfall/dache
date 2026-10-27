package dev.dache.core.model

import kotlin.time.Instant

/** One point of a time series. */
data class TimedValue(
    val at: Instant,
    val value: Double,
)
