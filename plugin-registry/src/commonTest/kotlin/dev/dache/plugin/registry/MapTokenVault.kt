package dev.dache.plugin.registry

import dev.dache.core.security.TokenKey
import dev.dache.core.security.TokenVault

class MapTokenVault : TokenVault {
    val tokens = mutableMapOf<TokenKey, String>()

    override suspend fun put(
        key: TokenKey,
        token: String,
    ) {
        tokens[key] = token
    }

    override suspend fun get(key: TokenKey): String? = tokens[key]

    override suspend fun delete(key: TokenKey) {
        tokens.remove(key)
    }
}
