package dev.dache.app.plugins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dache.core.model.ConnectedAccount
import dev.dache.plugin.api.IndexEntry
import dev.dache.plugin.registry.PendingInstall
import dev.dache.plugin.registry.Plugin
import dev.dache.plugin.registry.PluginManager
import dev.dache.plugin.registry.RegistryClient
import dev.dache.plugin.registry.RegistryStatus
import dev.dache.plugin.registry.Update
import dev.dache.plugin.registry.missingFrom
import dev.dache.plugin.registry.updatesIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the plugin screen is doing besides showing plugins: a download or check under way, or one awaiting consent.
 * [affectedAccounts] names the connected accounts whose saved tokens [pending] would send to new hosts or in a new
 * way, so the consent screen can say so.
 */
data class PluginWork(
    val busy: Boolean = false,
    val pending: PendingInstall? = null,
    val error: Throwable? = null,
    val affectedAccounts: List<String> = emptyList(),
)

data class PluginsUiState(
    val plugins: List<Plugin> = emptyList(),
    val registry: RegistryStatus = RegistryStatus.Unchecked,
    val allowSideload: Boolean = false,
    val work: PluginWork = PluginWork(),
) {
    private val index = (registry as? RegistryStatus.Ready)?.index

    val updates: List<Update> get() = index?.let { plugins.updatesIn(it) }.orEmpty()

    val available: List<IndexEntry> get() = index?.let { plugins.missingFrom(it) }.orEmpty()
}

/**
 * Drives the plugin screen. Every install goes through [PluginWork.pending] and the consent dialog, except an
 * update that keeps a verified plugin's hosts and auth, which installs as soon as it's verified.
 */
class PluginsViewModel(
    private val manager: PluginManager,
    private val registry: RegistryClient,
    private val accounts: Flow<List<ConnectedAccount>> = flowOf(emptyList()),
) : ViewModel() {
    private val work = MutableStateFlow(PluginWork())

    val state: StateFlow<PluginsUiState> =
        combine(manager.plugins, manager.registryStatus, manager.allowSideload, work, ::PluginsUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PluginsUiState())

    val bundledIds: Set<String> get() = manager.bundledIds

    init {
        if (manager.registryStatus.value == RegistryStatus.Unchecked) checkRegistry()
    }

    fun checkRegistry() {
        viewModelScope.launch { manager.checkRegistry() }
    }

    fun install(entry: IndexEntry) = prepare { manager.prepare(entry) }

    fun installFromLink(url: String) =
        prepare {
            registry.download(url).mapCatching { manager.prepareSideload(it).getOrThrow() }
        }

    fun installFromFile(text: String) = prepare { manager.prepareSideload(text) }

    private fun prepare(step: suspend () -> Result<PendingInstall>) {
        work.value = PluginWork(busy = true)
        viewModelScope.launch {
            step()
                .onSuccess { pending ->
                    if (pending.needsConsent) {
                        work.value = PluginWork(pending = pending, affectedAccounts = affectedAccounts(pending))
                    } else {
                        manager.install(pending)
                        work.value = PluginWork()
                    }
                }.onFailure { work.value = PluginWork(error = it) }
        }
    }

    /** Accounts connected to the plugin [pending] replaces, when it changes where their tokens go. */
    private suspend fun affectedAccounts(pending: PendingInstall): List<String> =
        if (pending.changesAccessForSavedTokens) {
            accounts.first().filter { it.providerId.id == pending.manifest.id }.map { it.displayName }
        } else {
            emptyList()
        }

    /** The user agreed on the consent screen. */
    fun confirm() {
        val pending = work.value.pending ?: return
        work.value = PluginWork(busy = true)
        viewModelScope.launch {
            manager.install(pending)
            work.value = PluginWork()
        }
    }

    fun dismiss() {
        work.update { PluginWork() }
    }

    fun remove(plugin: Plugin) {
        viewModelScope.launch { manager.remove(plugin.id) }
    }

    fun setAllowSideload(allow: Boolean) {
        viewModelScope.launch { manager.setAllowSideload(allow) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
