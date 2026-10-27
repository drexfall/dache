package dev.dache.app

import dev.dache.core.account.AccountRepository
import dev.dache.core.account.AccountStore
import dev.dache.core.dashboard.DashboardRepository
import dev.dache.core.model.ProviderId
import dev.dache.core.provider.DevOpsProvider
import dev.dache.core.provider.mock.MockProvider
import dev.dache.core.security.TokenVault
import dev.dache.core.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Manual DI root, created once per process by the platform entry point. */
class AppContainer(
    vault: TokenVault,
    accountStore: AccountStore,
    val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
) {
    // Mock data until the manifest engine and real plugins land.
    private val providers: Map<ProviderId, DevOpsProvider> =
        ProviderId.entries.associateWith { MockProvider(it, vault) }

    val accountRepository = AccountRepository(accountStore, vault, providers)
    val dashboardRepository = DashboardRepository(accountRepository, providers, scope)

    fun setConfirmExternalLinks(confirm: Boolean) {
        scope.launch { settingsStore.setConfirmExternalLinks(confirm) }
    }

    /** Fire-and-forget refresh for entry points outside the UI tree, e.g. a keyboard shortcut. */
    fun refreshInBackground() {
        scope.launch { dashboardRepository.refresh() }
    }
}
