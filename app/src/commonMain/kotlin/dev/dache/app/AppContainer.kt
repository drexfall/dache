package dev.dache.app

import dev.dache.app.security.AppLockController
import dev.dache.core.account.AccountRepository
import dev.dache.core.account.AccountStore
import dev.dache.core.actions.ActionService
import dev.dache.core.actions.WriteLog
import dev.dache.core.alerts.AlertSettings
import dev.dache.core.alerts.AlertStore
import dev.dache.core.alerts.BackgroundCheck
import dev.dache.core.dashboard.DashboardRepository
import dev.dache.core.provider.ProviderCatalog
import dev.dache.core.security.IntegrityWarningSettings
import dev.dache.core.security.TokenVault
import dev.dache.core.settings.AppLockSettings
import dev.dache.core.settings.SettingsStore
import dev.dache.plugin.registry.BundledPlugins
import dev.dache.plugin.registry.PluginManager
import dev.dache.plugin.registry.PluginStore
import dev.dache.plugin.registry.RegistryClient
import dev.dache.plugin.runtime.ManifestProvider
import dev.dache.plugin.runtime.RequestLogger
import dev.dache.plugin.runtime.http.createPluginHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Where the app keeps what it persists. Several are often one store implementing more than one of these. */
class AppStores(
    val accounts: AccountStore,
    val settings: SettingsStore,
    val alerts: AlertStore,
    val writeLog: WriteLog,
    val plugins: PluginStore,
)

/**
 * Manual DI root, created once per process by the platform entry point. Pass [requestLogger] only in debug
 * builds; release builds keep the default and log nothing about requests.
 */
class AppContainer(
    vault: TokenVault,
    stores: AppStores,
    val scope: CoroutineScope,
    requestLogger: RequestLogger = RequestLogger.None,
) {
    val settingsStore: SettingsStore = stores.settings
    val alertStore: AlertStore = stores.alerts
    val writeLog: WriteLog = stores.writeLog
    private val accountStore = stores.accounts
    private val pluginStore = stores.plugins

    private val httpClient = createPluginHttpClient()

    val registryClient = RegistryClient(httpClient)

    // Starts with the bundled plugins but isn't ready until installed ones are loaded over them.
    private val catalog =
        ProviderCatalog(BundledPlugins.providers(vault, httpClient, requestLogger), ready = false)

    val plugins =
        PluginManager(
            store = pluginStore,
            catalog = catalog,
            registry = registryClient,
            bundled = BundledPlugins.manifests,
            providerFor = { ManifestProvider(it, vault = vault, client = httpClient, logger = requestLogger) },
        ).apply { start(scope) }

    val accountRepository = AccountRepository(accountStore, vault, catalog)
    val dashboardRepository = DashboardRepository(accountRepository, scope)

    val backgroundCheck = BackgroundCheck(dashboardRepository, alertStore)
    val actionService = ActionService(accountRepository, dashboardRepository, writeLog)

    /** Process-wide, so the lock outlives the activity and covers every way back into the app. */
    val appLock = AppLockController(settingsStore.appLock, scope)

    fun setConfirmExternalLinks(confirm: Boolean) {
        scope.launch { settingsStore.setConfirmExternalLinks(confirm) }
    }

    fun setAppLock(settings: AppLockSettings) {
        scope.launch { settingsStore.setAppLock(settings) }
    }

    fun setAlertSettings(settings: AlertSettings) {
        scope.launch { alertStore.setAlertSettings(settings) }
    }

    fun setIntegrityWarning(settings: IntegrityWarningSettings) {
        scope.launch { settingsStore.setIntegrityWarning(settings) }
    }

    /** Checks the registry at most once a day and installs updates that keep each plugin's hosts and auth. */
    fun updatePluginsInBackground() {
        scope.launch {
            if (plugins.isCheckDue()) plugins.checkRegistry().onSuccess { plugins.autoUpdate(it) }
        }
    }

    /** Fire-and-forget refresh for entry points outside the UI tree, e.g. a keyboard shortcut. */
    fun refreshInBackground() {
        scope.launch { dashboardRepository.refresh() }
    }
}
