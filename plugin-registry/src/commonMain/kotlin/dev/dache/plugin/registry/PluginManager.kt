package dev.dache.plugin.registry

import dev.dache.core.provider.DevOpsProvider
import dev.dache.core.provider.ProviderCatalog
import dev.dache.plugin.api.AuthSpec
import dev.dache.plugin.api.IndexEntry
import dev.dache.plugin.api.PluginManifest
import dev.dache.plugin.api.RegistryIndex
import dev.dache.plugin.runtime.providerId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** A plugin the app can use: bundled, or installed over a bundled one or on its own. */
data class Plugin(
    val manifest: PluginManifest,
    val source: PluginSource,
) {
    val id: String get() = manifest.id
    val verified: Boolean get() = source != PluginSource.SIDELOADED
}

/**
 * A plugin downloaded and checked but not installed yet. [current] is what it would replace. It needs the user's
 * consent unless it updates a verified plugin without changing the hosts it calls or where the token goes.
 */
class PendingInstall(
    val manifest: PluginManifest,
    val json: String,
    val sha256: String,
    val source: PluginSource,
    val current: Plugin?,
) {
    val verified: Boolean get() = source != PluginSource.SIDELOADED

    val needsConsent: Boolean
        get() = current == null || !current.verified || !verified || changesAccess(current.manifest, manifest)

    /**
     * It replaces a plugin and changes the hosts it calls or where the token goes, so the tokens of accounts
     * already connected to that plugin would go to the new hosts or in the new way.
     */
    val changesAccessForSavedTokens: Boolean
        get() = current != null && changesAccess(current.manifest, manifest)
}

/** A newer version of an installed plugin in the registry. */
data class Update(
    val entry: IndexEntry,
    val current: Plugin,
) {
    /** Hosts or auth change, or it replaces a sideloaded plugin, so the user approves it again. */
    val needsApproval: Boolean
        get() = !current.verified || current.manifest.hosts != entry.hosts || current.manifest.auth != entry.auth
}

sealed interface RegistryStatus {
    data object Unchecked : RegistryStatus

    data object Checking : RegistryStatus

    data class Ready(
        val index: RegistryIndex,
    ) : RegistryStatus

    data class Failed(
        val error: Throwable,
    ) : RegistryStatus
}

/**
 * Keeps [catalog] in step with the bundled plugins and those installed in [store]: an installed plugin replaces
 * the bundled one with its id. Installs from the registry are verified by [registry]; sideloads need the opt-in.
 */
class PluginManager(
    private val store: PluginStore,
    private val catalog: ProviderCatalog,
    private val registry: RegistryClient,
    private val bundled: List<PluginManifest>,
    private val providerFor: (PluginManifest) -> DevOpsProvider,
    private val clock: Clock = Clock.System,
) {
    private val effective = MutableStateFlow(bundled.map { Plugin(it, PluginSource.BUNDLED) })
    private val status = MutableStateFlow<RegistryStatus>(RegistryStatus.Unchecked)

    /** Every plugin in use, by name. */
    val plugins: StateFlow<List<Plugin>> = effective.asStateFlow()

    val registryStatus: StateFlow<RegistryStatus> = status.asStateFlow()

    val allowSideload = store.state.map { it.allowSideload }

    /** Applies the stored plugins now and whenever they change. Until the first apply, [catalog] isn't ready. */
    fun start(scope: CoroutineScope) {
        scope.launch { store.state.collect(::apply) }
    }

    private fun apply(state: PluginState) {
        val installed = state.installed.mapNotNull { stored -> stored.toPlugin() }
        val byId = bundled.associate { it.id to Plugin(it, PluginSource.BUNDLED) } + installed.associateBy { it.id }
        val plugins = byId.values.sortedBy { it.manifest.name.lowercase() }
        effective.value = plugins
        catalog.replace(plugins.associate { plugin -> plugin.manifest.providerId() to providerFor(plugin.manifest) })
    }

    /** Fetches the signed index. A failure leaves installed plugins as they are. */
    suspend fun checkRegistry(): Result<RegistryIndex> {
        status.value = RegistryStatus.Checking
        val seen = store.state.first().lastSerial
        return registry
            .index(minSerial = seen)
            .onSuccess { index ->
                store.update {
                    it.copy(
                        lastSerial = maxOf(it.lastSerial, index.serial),
                        lastCheckedMillis = clock.now().toEpochMilliseconds(),
                    )
                }
                status.value = RegistryStatus.Ready(index)
            }.onFailure { status.value = RegistryStatus.Failed(it) }
    }

    /** Whether a day has passed since the last successful check. */
    suspend fun isCheckDue(): Boolean =
        clock.now().toEpochMilliseconds() - store.state.first().lastCheckedMillis >= CHECK_INTERVAL_MS

    /** Installs every update that keeps the plugin's hosts and auth; returns those still waiting for approval. */
    suspend fun autoUpdate(index: RegistryIndex): List<Update> {
        val (waiting, silent) = plugins.value.updatesIn(index).partition { it.needsApproval }
        silent.forEach { update -> prepare(update.entry).onSuccess { if (!it.needsConsent) install(it) } }
        return waiting
    }

    /** Downloads and verifies [entry], ready for the consent screen. */
    suspend fun prepare(entry: IndexEntry): Result<PendingInstall> =
        registry.manifest(entry).map { verified ->
            PendingInstall(
                verified.manifest,
                verified.json,
                verified.sha256,
                PluginSource.REGISTRY,
                plugins.value.byId(entry.id),
            )
        }

    /** Checks a manifest from outside the registry. Fails unless sideloading is switched on. */
    suspend fun prepareSideload(json: String): Result<PendingInstall> =
        runCatching {
            if (!store.state.first().allowSideload) throw RegistryException("Turn on installing from files first.")
            val manifest = RegistryClient.load(json, "This file")
            val digest = sha256Hex(json.encodeToByteArray())
            PendingInstall(manifest, json, digest, PluginSource.SIDELOADED, plugins.value.byId(manifest.id))
        }

    suspend fun install(pending: PendingInstall) {
        val stored = InstalledPlugin(pending.json, pending.source, pending.sha256)
        store.update { state ->
            state.copy(installed = state.installed.filterNot { it.toPlugin()?.id == pending.manifest.id } + stored)
        }
    }

    /** Removes an installed plugin; a bundled one with the same id takes its place again. */
    suspend fun remove(id: String) {
        store.update { state -> state.copy(installed = state.installed.filterNot { it.toPlugin()?.id == id }) }
    }

    suspend fun setAllowSideload(allow: Boolean) {
        store.update { it.copy(allowSideload = allow) }
    }

    /** Ids that removing an installed plugin falls back to, rather than taking the provider away. */
    val bundledIds: Set<String> = bundled.map { it.id }.toSet()

    private companion object {
        const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}

private fun List<Plugin>.byId(id: String): Plugin? = firstOrNull { it.id == id }

/** Newer versions of these plugins in [index]. */
fun List<Plugin>.updatesIn(index: RegistryIndex): List<Update> {
    val current = associateBy { it.id }
    return index.plugins.mapNotNull { entry ->
        current[entry.id]?.takeIf { isNewer(entry.version, it.manifest.version) }?.let { Update(entry, it) }
    }
}

/** Registry plugins that aren't among these. */
fun List<Plugin>.missingFrom(index: RegistryIndex): List<IndexEntry> {
    val ids = map { it.id }.toSet()
    return index.plugins.filter { it.id !in ids }
}

/** A stored manifest this build can no longer read (e.g. after a downgrade) is skipped, not fatal. */
private fun InstalledPlugin.toPlugin(): Plugin? =
    runCatching { Plugin(RegistryClient.load(json, "plugin"), source) }.getOrNull()

/** Hosts it may call or where the token goes differ: what the consent screen exists for. */
fun changesAccess(
    before: PluginManifest,
    after: PluginManifest,
): Boolean = before.hosts.toSet() != after.hosts.toSet() || before.auth != after.auth

/** MAJOR.MINOR.PATCH comparison; anything unparseable never counts as newer. */
fun isNewer(
    candidate: String,
    current: String,
): Boolean {
    val a = candidate.split('.').map { it.toIntOrNull() }
    val b = current.split('.').map { it.toIntOrNull() }
    if (null in a || null in b) return false
    return a.zip(b).firstOrNull { (x, y) -> x != y }?.let { (x, y) -> x!! > y!! } ?: (a.size > b.size)
}

/** For [AuthSpec] values on screen: `bearer`, `header X-Key`, `query key`. */
fun AuthSpec.describe(): String =
    when (this) {
        AuthSpec.Bearer -> "Authorization: Bearer"
        is AuthSpec.Header -> "header $name"
        is AuthSpec.Query -> "query parameter $name"
    }
