package dev.dache.core.fakes

import dev.dache.core.account.AccountStore
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.security.TokenKey
import dev.dache.core.security.TokenVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class InMemoryAccountStore(
    initial: List<ConnectedAccount> = emptyList(),
) : AccountStore {
    private val state = MutableStateFlow(initial)
    override val accounts: StateFlow<List<ConnectedAccount>> = state

    override suspend fun update(transform: (List<ConnectedAccount>) -> List<ConnectedAccount>) {
        state.update(transform)
    }
}

class InMemoryTokenVault : TokenVault {
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
