package dev.dache.core.provider

import dev.dache.core.model.ProviderId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * The providers the app can use right now. Installing, updating or removing a plugin replaces them. A catalog
 * made not [ready] waits for its first [replace], so nothing refreshes before installed plugins are loaded.
 */
class ProviderCatalog(
    initial: Map<ProviderId, DevOpsProvider> = emptyMap(),
    ready: Boolean = true,
) {
    private val state = MutableStateFlow(initial)
    private val loaded = MutableStateFlow(ready)

    val providers: StateFlow<Map<ProviderId, DevOpsProvider>> = state.asStateFlow()

    operator fun get(id: ProviderId): DevOpsProvider? = state.value[id]

    /** The installed provider's own id, which carries its name and links, or [id] when it isn't installed. */
    fun resolve(id: ProviderId): ProviderId = state.value[id]?.id ?: id

    fun replace(providers: Map<ProviderId, DevOpsProvider>) {
        state.value = providers
        loaded.value = true
    }

    suspend fun awaitReady() {
        loaded.first { it }
    }
}
