package dev.dache.core.account

import dev.dache.core.model.AccountSetting
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.Profile
import dev.dache.core.model.Profiles
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
import kotlinx.coroutines.flow.first
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Connects and removes accounts, and groups them into profiles. Removing an account deletes its token too, and
 * deleting a profile removes its accounts. Accounts come back with their provider's full id from [catalog], so its
 * name and links follow the installed plugin.
 */
@Suppress("TooManyFunctions")
class AccountRepository(
    private val store: AccountStore,
    private val vault: TokenVault,
    val catalog: ProviderCatalog,
    private val newId: () -> String = ::randomId,
    private val profileStore: ProfileStore = InMemoryProfileStore(),
) {
    constructor(
        store: AccountStore,
        vault: TokenVault,
        providers: Map<ProviderId, DevOpsProvider>,
        newId: () -> String = ::randomId,
        profileStore: ProfileStore = InMemoryProfileStore(),
    ) : this(store, vault, ProviderCatalog(providers), newId, profileStore)

    private val providers: Map<ProviderId, DevOpsProvider> get() = catalog.providers.value

    val profiles: Flow<Profiles> = profileStore.profiles

    /** Every account, in every profile. */
    val accounts: Flow<List<ConnectedAccount>> =
        combine(store.accounts, catalog.providers, profileStore.profiles) { accounts, _, profiles ->
            accounts.map { it.copy(providerId = catalog.resolve(it.providerId), profileId = profiles.owner(it)) }
        }

    /** The active profile's accounts: what the dashboard shows and refreshes. */
    val activeAccounts: Flow<List<ConnectedAccount>> =
        combine(accounts, profileStore.profiles) { accounts, profiles ->
            accounts.filter { it.profileId == profiles.active.id }
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
        /** The profile to add the account to; the active one when null. */
        profileId: String? = null,
    ): Result<ConnectedAccount> {
        val provider = providers[providerId] ?: return Result.failure(ProviderError.NotFound())
        val trimmed = token.trim()
        val values = clean(settings)
        val profile =
            profileId ?: profileStore.profiles
                .first()
                .active.id
        return provider.validateCredentials(trimmed, values).fold(
            onSuccess = { info ->
                val account =
                    ConnectedAccount(
                        id = newId(),
                        providerId = providerId,
                        displayName = info.displayName,
                        settings = values,
                        profileId = profile,
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

    /** Adds a profile named [name] and makes it the active one. Returns null when [name] is blank. */
    suspend fun createProfile(name: String): Profile? {
        val trimmed = name.trim().takeIf { it.isNotEmpty() } ?: return null
        val profile = Profile(newId(), trimmed)
        profileStore.updateProfiles { Profiles(it.all + profile, profile.id) }
        return profile
    }

    /** Renames profile [id]; a blank [name] is ignored. */
    suspend fun renameProfile(
        id: String,
        name: String,
    ) {
        val trimmed = name.trim().takeIf { it.isNotEmpty() } ?: return
        profileStore.updateProfiles { profiles ->
            profiles.copy(all = profiles.all.map { if (it.id == id) it.copy(name = trimmed) else it })
        }
    }

    /**
     * Deletes profile [id] with its accounts and their tokens. The last profile can't be deleted; returns whether
     * anything was. When the active profile goes, the one before it (or the new first one) becomes active.
     */
    suspend fun deleteProfile(id: String): Boolean {
        val profiles = profileStore.profiles.first()
        if (profiles.all.size <= 1 || profiles.all.none { it.id == id }) return false
        accounts.first().filter { it.profileId == id }.forEach { remove(it) }
        profileStore.updateProfiles { current ->
            val index = current.all.indexOfFirst { it.id == id }
            val remaining = current.all.filterNot { it.id == id }
            val active = if (current.activeId == id) remaining[(index - 1).coerceAtLeast(0)].id else current.activeId
            Profiles(remaining, active)
        }
        return true
    }

    suspend fun switchProfile(id: String) {
        profileStore.updateProfiles { if (it.all.any { p -> p.id == id }) it.copy(activeId = id) else it }
    }

    /** Makes the profile after the active one active, wrapping around, and returns it. */
    suspend fun cycleProfile(): Profile {
        var next = Profile.DEFAULT
        profileStore.updateProfiles { profiles ->
            next = profiles.next
            profiles.copy(activeId = next.id)
        }
        return next
    }

    /** Moves [account] to profile [profileId]; its token stays where it is. */
    suspend fun moveAccount(
        account: ConnectedAccount,
        profileId: String,
    ) {
        if (profileStore.profiles
                .first()
                .all
                .none { it.id == profileId }
        ) {
            return
        }
        store.update { list -> list.map { if (it.id == account.id) it.copy(profileId = profileId) else it } }
    }
}

/** The profile [account] belongs to: its own, or the first one when that profile no longer exists. */
private fun Profiles.owner(account: ConnectedAccount): String =
    account.profileId.takeIf { id -> all.any { it.id == id } } ?: all.first().id

fun ConnectedAccount.tokenKey(): TokenKey = TokenKey(pluginId = providerId.id, accountId = id)

@OptIn(ExperimentalUuidApi::class)
private fun randomId(): String = Uuid.random().toString()
