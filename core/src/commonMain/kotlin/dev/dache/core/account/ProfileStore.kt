package dev.dache.core.account

import dev.dache.core.model.Profiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Persists the profiles and which one is active. Implementations always emit [Profiles.normalized] values. */
interface ProfileStore {
    val profiles: Flow<Profiles>

    suspend fun updateProfiles(transform: (Profiles) -> Profiles)
}

/** Keeps profiles in memory only; for tests and previews. */
class InMemoryProfileStore(
    initial: Profiles = Profiles(),
) : ProfileStore {
    private val state = MutableStateFlow(initial.normalized())
    override val profiles: StateFlow<Profiles> = state

    override suspend fun updateProfiles(transform: (Profiles) -> Profiles) {
        state.update { transform(it).normalized() }
    }
}
