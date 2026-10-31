package dev.dache.app

import dev.dache.core.account.AccountRepository
import dev.dache.core.account.AccountStore
import dev.dache.core.dashboard.DashboardRepository
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderId
import dev.dache.core.provider.DevOpsProvider
import dev.dache.core.provider.mock.MockProvider
import dev.dache.core.security.IntegrityWarningSettings
import dev.dache.core.security.TokenKey
import dev.dache.core.security.TokenVault
import dev.dache.core.settings.AppLockSettings
import dev.dache.core.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration

class InMemoryAccountStore : AccountStore {
    private val state = MutableStateFlow<List<ConnectedAccount>>(emptyList())
    override val accounts: StateFlow<List<ConnectedAccount>> = state

    override suspend fun update(transform: (List<ConnectedAccount>) -> List<ConnectedAccount>) = state.update(transform)
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

class InMemorySettingsStore : SettingsStore {
    private val confirm = MutableStateFlow(true)
    override val confirmExternalLinks: StateFlow<Boolean> = confirm

    override suspend fun setConfirmExternalLinks(confirm: Boolean) {
        this.confirm.value = confirm
    }

    override val appLock = MutableStateFlow(AppLockSettings())

    override suspend fun setAppLock(settings: AppLockSettings) {
        appLock.value = settings
    }

    override val integrityWarning = MutableStateFlow(IntegrityWarningSettings())

    override suspend fun setIntegrityWarning(settings: IntegrityWarningSettings) {
        integrityWarning.value = settings
    }
}

class TestGraph(
    scope: CoroutineScope,
    /** Replaces the mock provider for its id. */
    overrides: (InMemoryTokenVault) -> Map<ProviderId, DevOpsProvider> = { emptyMap() },
) {
    val vault = InMemoryTokenVault()
    private val providers =
        ProviderId.entries.associateWith { MockProvider(it, vault, latency = Duration.ZERO) } + overrides(vault)
    val accounts = AccountRepository(InMemoryAccountStore(), vault, providers)
    val dashboard = DashboardRepository(accounts, providers, scope)
}
