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
 *
 * Implementations throw [VaultUnavailableException] when the platform store can't be used at all, e.g. no
 * keyring daemon on Linux. A token that exists but can no longer be decrypted reads as missing.
 */
interface TokenVault {
    suspend fun put(
        key: TokenKey,
        token: String,
    )

    suspend fun get(key: TokenKey): String?

    suspend fun delete(key: TokenKey)
}

/** The platform secret store is missing or refused the operation. Never carries the token. */
class VaultUnavailableException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
