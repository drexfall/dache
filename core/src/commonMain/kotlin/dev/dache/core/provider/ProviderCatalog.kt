package dev.dache.core.provider

import dev.dache.core.model.ProviderId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The providers the app can use right now. Installing, updating or removing a plugin replaces them. */
class ProviderCatalog(
    initial: Map<ProviderId, DevOpsProvider> = emptyMap(),
) {
    private val state = MutableStateFlow(initial)

    val providers: StateFlow<Map<ProviderId, DevOpsProvider>> = state.asStateFlow()

    operator fun get(id: ProviderId): DevOpsProvider? = state.value[id]

    /** The installed provider's own id, which carries its name and links, or [id] when it isn't installed. */
    fun resolve(id: ProviderId): ProviderId = state.value[id]?.id ?: id

    fun replace(providers: Map<ProviderId, DevOpsProvider>) {
        state.value = providers
    }
}
