package dev.dache.core.account

import dev.dache.core.model.AccountSetting
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.core.model.TokenCheck
import dev.dache.core.model.TokenPermission
import dev.dache.core.provider.DevOpsProvider
import dev.dache.core.provider.ProviderCatalog
import dev.dache.core.security.TokenKey
import dev.dache.core.security.TokenVault
import dev.dache.core.security.VaultUnavailableException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Connects and removes accounts. Removing an account deletes its token too. Accounts come back with their
 * provider's full id from [catalog], so its name and links follow the installed plugin.
 */
class AccountRepository(
    private val store: AccountStore,
    private val vault: TokenVault,
    val catalog: ProviderCatalog,
    private val newId: () -> String = ::randomId,
) {
    constructor(
        store: AccountStore,
        vault: TokenVault,
        providers: Map<ProviderId, DevOpsProvider>,
        newId: () -> String = ::randomId,
    ) : this(store, vault, ProviderCatalog(providers), newId)

    private val providers: Map<ProviderId, DevOpsProvider> get() = catalog.providers.value

    val accounts: Flow<List<ConnectedAccount>> =
        combine(store.accounts, catalog.providers) { accounts, _ ->
            accounts.map { it.copy(providerId = catalog.resolve(it.providerId)) }
        }

    /** What [providerId]'s token check reports on, to list before a token is checked. */
    fun tokenPermissions(providerId: ProviderId): List<TokenPermission> =
        providers[providerId]?.tokenPermissions.orEmpty()

    /** The settings [providerId] asks for besides the token. */
    fun settings(providerId: ProviderId): List<AccountSetting> = providers[providerId]?.accountSettings.orEmpty()

    /**
     * Validates [token] and [settings] with the provider, then stores the token and the account. Fails with
     * [VaultUnavailableException] when the token can't be stored; no account is added then.
     */
    suspend fun connect(
        providerId: ProviderId,
        token: String,
        settings: Map<String, String> = emptyMap(),
    ): Result<ConnectedAccount> {
        val provider = providers[providerId] ?: return Result.failure(ProviderError.NotFound())
        val trimmed = token.trim()
        val values = clean(settings)
        return provider.validateCredentials(trimmed, values).fold(
            onSuccess = { info ->
                val account =
                    ConnectedAccount(
                        id = newId(),
                        providerId = providerId,
                        displayName = info.displayName,
                        settings = values,
                    )
                save(account, trimmed)
            },
            onFailure = { Result.failure(it) },
        )
    }

    /** Checks [token] and which permissions it has, without connecting anything. */
    suspend fun checkToken(
        providerId: ProviderId,
        token: String,
        settings: Map<String, String> = emptyMap(),
    ): Result<TokenCheck> {
        val provider = providers[providerId] ?: return Result.failure(ProviderError.NotFound())
        return provider.checkToken(token.trim(), clean(settings))
    }

    /** The same check for a connected account, with its stored token. */
    suspend fun checkToken(account: ConnectedAccount): Result<TokenCheck> {
        val token =
            try {
                vault.get(account.tokenKey())
            } catch (e: VaultUnavailableException) {
                return Result.failure(e)
            }
        return token?.let { checkToken(account.providerId, it, account.settings) }
            ?: Result.failure(ProviderError.Unauthorized())
    }

    private fun clean(settings: Map<String, String>): Map<String, String> =
        settings.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }

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
