package dev.dache.core.model

import kotlinx.serialization.Serializable

/** A provider account the user connected. Holds no secrets; the token lives in the TokenVault. */
@Serializable
data class ConnectedAccount(
    val id: String,
    val providerId: ProviderId,
    val displayName: String,
)

/** Identity returned by a successful credential check. */
data class AccountInfo(
    val displayName: String,
)
