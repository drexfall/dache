package dev.dache.core.account

import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.core.provider.DevOpsProvider
import dev.dache.core.security.TokenKey
import dev.dache.core.security.TokenVault
import dev.dache.core.security.VaultUnavailableException
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

    /**
     * Validates [token] with the provider, then stores the token and the account. Fails with
     * [VaultUnavailableException] when the token can't be stored; no account is added then.
     */
    suspend fun connect(
        providerId: ProviderId,
        token: String,
    ): Result<ConnectedAccount> {
        val provider = providers[providerId] ?: return Result.failure(ProviderError.NotFound())
        val trimmed = token.trim()
        return provider.validateCredentials(trimmed).fold(
            onSuccess = { info ->
                save(ConnectedAccount(id = newId(), providerId = providerId, displayName = info.displayName), trimmed)
            },
            onFailure = { Result.failure(it) },
        )
    }

    private suspend fun save(
        account: ConnectedAccount,
        token: String,
    ): Result<ConnectedAccount> {
        try {
            vault.put(account.tokenKey(), token)
        } catch (e: VaultUnavailableException) {
            return Result.failure(e)
        }
        store.update { it + account }
        return Result.success(account)
    }

    suspend fun remove(account: ConnectedAccount) {
        vault.delete(account.tokenKey())
        store.update { list -> list.filterNot { it.id == account.id } }
    }
}

fun ConnectedAccount.tokenKey(): TokenKey = TokenKey(pluginId = providerId.id, accountId = id)

@OptIn(ExperimentalUuidApi::class)
private fun randomId(): String = Uuid.random().toString()
