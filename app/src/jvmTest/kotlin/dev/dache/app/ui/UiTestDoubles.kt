package dev.dache.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.dache.app.AppContainer
import dev.dache.app.AppStores
import dev.dache.app.InMemoryAccountStore
import dev.dache.app.InMemorySettingsStore
import dev.dache.app.InMemoryTokenVault
import dev.dache.app.ui.components.LinkOpener
import dev.dache.app.ui.components.LocalLinkOpener
import dev.dache.app.ui.layout.FormFactor
import dev.dache.app.ui.layout.LocalFormFactor
import dev.dache.app.ui.theme.DacheTheme
import dev.dache.core.actions.WriteLog
import dev.dache.core.actions.WriteLogEntry
import dev.dache.core.alerts.AlertSettings
import dev.dache.core.alerts.AlertStore
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.security.TokenKey
import dev.dache.plugin.registry.PluginState
import dev.dache.plugin.registry.PluginStore
import dev.dache.plugin.runtime.http.createPluginHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking

/** Theme, a link opener that opens nothing and the desktop layout, which every screen under test expects. */
@Composable
fun TestHost(
    formFactor: FormFactor = FormFactor.EXPANDED,
    content: @Composable () -> Unit,
) {
    val owner =
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    DacheTheme(dark = false) {
        CompositionLocalProvider(
            LocalLinkOpener provides LinkOpener {},
            LocalFormFactor provides formFactor,
            LocalViewModelStoreOwner provides owner,
        ) {
            Box(Modifier.requiredSize(1024.dp, 768.dp)) { content() }
        }
    }
}

class InMemoryAlertStore : AlertStore {
    private val settings = MutableStateFlow(AlertSettings())
    private var reported: Set<String>? = null
    override val alertSettings: Flow<AlertSettings> = settings

    override suspend fun setAlertSettings(settings: AlertSettings) {
        this.settings.value = settings
    }

    override suspend fun reportedAlerts(): Set<String>? = reported

    override suspend fun setReportedAlerts(keys: Set<String>) {
        reported = keys
    }
}

class InMemoryWriteLog : WriteLog {
    private val log = MutableStateFlow<List<WriteLogEntry>>(emptyList())
    override val entries: Flow<List<WriteLogEntry>> = log

    override suspend fun append(entry: WriteLogEntry) = log.update { listOf(entry) + it }
}

class InMemoryPluginStore(
    initial: PluginState = PluginState(),
) : PluginStore {
    override val state = MutableStateFlow(initial)

    override suspend fun update(transform: (PluginState) -> PluginState) = state.update(transform)
}

/** Every request fails with 404: the bundled plugins are real, so no account can refresh and no registry answers. */
fun offlineClient(): HttpClient = createPluginHttpClient(MockEngine { respondError(HttpStatusCode.NotFound) })

/**
 * A whole app graph in memory with the bundled plugins and no network. [accounts] are connected already, with a
 * token in the vault; the registry check isn't due, so nothing is fetched on start but the accounts themselves.
 */
fun testContainer(
    accounts: List<ConnectedAccount> = emptyList(),
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
): AppContainer {
    val vault = InMemoryTokenVault()
    val accountStore = InMemoryAccountStore()
    runBlocking {
        accountStore.update { accounts }
        accounts.forEach { vault.put(TokenKey(it.providerId.id, it.id), "token-${it.id}") }
    }
    val settings = InMemorySettingsStore()
    return AppContainer(
        vault = vault,
        stores =
            AppStores(
                accounts = accountStore,
                settings = settings,
                alerts = InMemoryAlertStore(),
                writeLog = InMemoryWriteLog(),
                plugins = InMemoryPluginStore(PluginState(lastCheckedMillis = System.currentTimeMillis())),
            ),
        scope = scope,
        httpClient = offlineClient(),
    )
}
