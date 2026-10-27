package dev.dache.core.account

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.dache.core.model.ConnectedAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath

class DataStoreAccountStore(
    private val dataStore: DataStore<Preferences>,
) : AccountStore {
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

    companion object {
        private val KEY = stringPreferencesKey("accounts")
        private val json = Json { ignoreUnknownKeys = true }

        /** [path] must end in `.preferences_pb`. */
        fun create(path: String): DataStoreAccountStore =
            DataStoreAccountStore(PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() }))
    }
}
