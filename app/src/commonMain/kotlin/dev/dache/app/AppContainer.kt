package dev.dache.app

import dev.dache.app.security.AppLockController
import dev.dache.core.account.AccountRepository
import dev.dache.core.account.AccountStore
import dev.dache.core.dashboard.DashboardRepository
import dev.dache.core.model.ProviderId
import dev.dache.core.provider.DevOpsProvider
import dev.dache.core.security.IntegrityWarningSettings
import dev.dache.core.security.TokenVault
import dev.dache.core.settings.AppLockSettings
import dev.dache.core.settings.SettingsStore
import dev.dache.plugin.registry.BundledPlugins
import dev.dache.plugin.runtime.RequestLogger
import dev.dache.plugin.runtime.http.createPluginHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Manual DI root, created once per process by the platform entry point. Pass [requestLogger] only in debug
 * builds; release builds keep the default and log nothing about requests.
 */
class AppContainer(
    vault: TokenVault,
    accountStore: AccountStore,
    val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
    requestLogger: RequestLogger = RequestLogger.None,
) {
    private val providers: Map<ProviderId, DevOpsProvider> =
        BundledPlugins.providers(vault, createPluginHttpClient(), requestLogger)

    val accountRepository = AccountRepository(accountStore, vault, providers)
    val dashboardRepository = DashboardRepository(accountRepository, providers, scope)

    /** Process-wide, so the lock outlives the activity and covers every way back into the app. */
    val appLock = AppLockController(settingsStore.appLock, scope)

    fun setConfirmExternalLinks(confirm: Boolean) {
        scope.launch { settingsStore.setConfirmExternalLinks(confirm) }
    }

    fun setAppLock(settings: AppLockSettings) {
        scope.launch { settingsStore.setAppLock(settings) }
    }

    fun setIntegrityWarning(settings: IntegrityWarningSettings) {
        scope.launch { settingsStore.setIntegrityWarning(settings) }
    }

    /** Fire-and-forget refresh for entry points outside the UI tree, e.g. a keyboard shortcut. */
    fun refreshInBackground() {
        scope.launch { dashboardRepository.refresh() }
    }
}
