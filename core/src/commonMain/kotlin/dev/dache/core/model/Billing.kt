package dev.dache.core.model

import kotlinx.serialization.Serializable

/** Account spend, for providers that bill by usage. Amounts are in [currency]. */
@Serializable
data class Billing(
    val currency: String,
    val monthToDateUsage: Double,
    val accountBalance: Double,
    /** Cost per UTC day, oldest first. */
    val dailyCost: List<TimedValue>,
)
