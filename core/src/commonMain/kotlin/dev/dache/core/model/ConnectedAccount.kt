package dev.dache.core.model

import kotlinx.serialization.Serializable

/** A provider account the user connected. Holds no secrets; the token lives in the TokenVault. */
@Serializable
data class ConnectedAccount(
    val id: String,
    val providerId: ProviderId,
    val displayName: String,
    /** Values for the provider's [AccountSetting]s, by key. Blank optional settings are left out. */
    val settings: Map<String, String> = emptyMap(),
)

/** A non-secret value the user gives when connecting, such as a team to scope requests to. */
data class AccountSetting(
    val key: String,
    val label: String,
    val hint: String? = null,
    val required: Boolean = false,
)

/** Identity returned by a successful credential check. */
data class AccountInfo(
    val displayName: String,
)
