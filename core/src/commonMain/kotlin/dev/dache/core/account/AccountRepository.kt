package dev.dache.core.account

import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.core.provider.DevOpsProvider
import dev.dache.core.security.TokenKey
import dev.dache.core.security.TokenVault
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Connects and removes accounts. Removing an account deletes its token too. */
class AccountRepository(
    private val store: AccountStore,
    private val vault: TokenVault,
    private val providers: Map<ProviderId, DevOpsProvider>,
    private val newId: () -> String = ::randomId,
) {
    val accounts: Flow<List<ConnectedAccount>> = store.accounts

    /** Validates [token] with the provider, then stores the token and the account. */
    suspend fun connect(
        providerId: ProviderId,
        token: String,
    ): Result<ConnectedAccount> {
        val provider = providers[providerId] ?: return Result.failure(ProviderError.NotFound())
        val trimmed = token.trim()
        return provider.validateCredentials(trimmed).map { info ->
            val account = ConnectedAccount(id = newId(), providerId = providerId, displayName = info.displayName)
            vault.put(account.tokenKey(), trimmed)
            store.update { it + account }
            account
        }
    }

    suspend fun remove(account: ConnectedAccount) {
        vault.delete(account.tokenKey())
        store.update { list -> list.filterNot { it.id == account.id } }
    }
}

fun ConnectedAccount.tokenKey(): TokenKey = TokenKey(pluginId = providerId.id, accountId = id)

@OptIn(ExperimentalUuidApi::class)
private fun randomId(): String = Uuid.random().toString()
