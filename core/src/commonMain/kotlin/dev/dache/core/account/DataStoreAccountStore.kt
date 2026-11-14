package dev.dache.core.account

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.Profile
import dev.dache.core.model.Profiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath

/** Accounts and the profiles that group them, in one file so neither outlives the other's format. */
class DataStoreAccountStore(
    private val dataStore: DataStore<Preferences>,
) : AccountStore,
    ProfileStore {
    override val accounts: Flow<List<ConnectedAccount>> =
        dataStore.data.map { prefs ->
            prefs[KEY]?.let { json.decodeFromString<List<ConnectedAccount>>(it) }.orEmpty()
        }

    override suspend fun update(transform: (List<ConnectedAccount>) -> List<ConnectedAccount>) {
        dataStore.edit { prefs ->
            val current = prefs[KEY]?.let { json.decodeFromString<List<ConnectedAccount>>(it) }.orEmpty()
            prefs[KEY] = json.encodeToString(transform(current))
        }
    }

    override val profiles: Flow<Profiles> = dataStore.data.map(::readProfiles)

    override suspend fun updateProfiles(transform: (Profiles) -> Profiles) {
        dataStore.edit { prefs ->
            val next = transform(readProfiles(prefs)).normalized()
            prefs[PROFILES_KEY] = json.encodeToString(next.all)
            prefs[ACTIVE_PROFILE_KEY] = next.activeId
        }
    }

    private fun readProfiles(prefs: Preferences): Profiles =
        Profiles(
            all = prefs[PROFILES_KEY]?.let { json.decodeFromString<List<Profile>>(it) }.orEmpty(),
            activeId = prefs[ACTIVE_PROFILE_KEY] ?: Profile.DEFAULT_ID,
        ).normalized()

    companion object {
        private val KEY = stringPreferencesKey("accounts")
        private val PROFILES_KEY = stringPreferencesKey("profiles")
        private val ACTIVE_PROFILE_KEY = stringPreferencesKey("active_profile")
        private val json = Json { ignoreUnknownKeys = true }

        /** [path] must end in `.preferences_pb`. */
        fun create(path: String): DataStoreAccountStore =
            DataStoreAccountStore(PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() }))
    }
}
