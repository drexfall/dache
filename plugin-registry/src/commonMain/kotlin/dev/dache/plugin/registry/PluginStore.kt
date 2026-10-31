package dev.dache.plugin.registry

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath

/** Where a plugin came from. Only registry plugins, whose index was signed, and bundled ones count as verified. */
@Serializable
enum class PluginSource { BUNDLED, REGISTRY, SIDELOADED }

/** An installed manifest, kept as the exact text that was verified. */
@Serializable
data class InstalledPlugin(
    val json: String,
    val source: PluginSource,
    val sha256: String,
)

/**
 * Everything the plugin manager persists. [lastSerial] is the newest registry index accepted, so an older one
 * can't be replayed; [allowSideload] is the opt-in for plugins from outside the registry.
 */
@Serializable
data class PluginState(
    val installed: List<InstalledPlugin> = emptyList(),
    val lastSerial: Long = 0,
    val lastCheckedMillis: Long = 0,
    val allowSideload: Boolean = false,
)

interface PluginStore {
    val state: Flow<PluginState>

    suspend fun update(transform: (PluginState) -> PluginState)
}

class DataStorePluginStore(
    private val dataStore: DataStore<Preferences>,
) : PluginStore {
    override val state: Flow<PluginState> = dataStore.data.map { decode(it[KEY]) }

    override suspend fun update(transform: (PluginState) -> PluginState) {
        dataStore.edit { prefs ->
            prefs[KEY] =
                json.encodeToString(PluginState.serializer(), transform(decode(prefs[KEY])))
        }
    }

    private fun decode(text: String?): PluginState =
        text?.let { json.decodeFromString(PluginState.serializer(), it) } ?: PluginState()

    companion object {
        private val KEY = stringPreferencesKey("plugins")
        private val json = Json { ignoreUnknownKeys = true }

        /** [path] must end in `.preferences_pb`. */
        fun create(path: String): DataStorePluginStore =
            DataStorePluginStore(PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() }))
    }
}
