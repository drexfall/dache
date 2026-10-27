package dev.dache.core.model

/** Account spend, for providers that bill by usage. Amounts are in [currency]. */
data class Billing(
    val currency: String,
    val monthToDateUsage: Double,
    val accountBalance: Double,
    /** Cost per UTC day, oldest first. */
    val dailyCost: List<TimedValue>,
)
