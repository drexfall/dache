package dev.dache.core.security

/** Tokens are keyed by plugin and account so one plugin can never read another's secret. */
data class TokenKey(
    val pluginId: String,
    val accountId: String,
) {
    override fun toString(): String = "$pluginId/$accountId"
}

/**
 * The only place tokens may be stored. Android: Keystore-backed Tink. Linux: Secret Service via libsecret.
 */
interface TokenVault {
    suspend fun put(
        key: TokenKey,
        token: String,
    )

    suspend fun get(key: TokenKey): String?

    suspend fun delete(key: TokenKey)
}
