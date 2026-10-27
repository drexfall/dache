package dev.dache.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

/** Non-secret user preferences. */
interface SettingsStore {
    /** Whether to ask before leaving the app for an external link. Defaults to true. */
    val confirmExternalLinks: Flow<Boolean>

    suspend fun setConfirmExternalLinks(confirm: Boolean)
}

class DataStoreSettingsStore(
    private val dataStore: DataStore<Preferences>,
) : SettingsStore {
    override val confirmExternalLinks: Flow<Boolean> = dataStore.data.map { it[CONFIRM_LINKS] ?: true }

    override suspend fun setConfirmExternalLinks(confirm: Boolean) {
        dataStore.edit { it[CONFIRM_LINKS] = confirm }
    }

    companion object {
        private val CONFIRM_LINKS = booleanPreferencesKey("confirm_external_links")

        /** [path] must end in `.preferences_pb` and must not be shared with another DataStore. */
        fun create(path: String): DataStoreSettingsStore =
            DataStoreSettingsStore(PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() }))
    }
}
