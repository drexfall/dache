package dev.dache.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

/** Non-secret user preferences. */
interface SettingsStore {
    /** Whether to ask before leaving the app for an external link. Defaults to true. */
    val confirmExternalLinks: Flow<Boolean>

    suspend fun setConfirmExternalLinks(confirm: Boolean)

    val appLock: Flow<AppLockSettings>

    suspend fun setAppLock(settings: AppLockSettings)
}

class DataStoreSettingsStore(
    private val dataStore: DataStore<Preferences>,
) : SettingsStore {
    override val confirmExternalLinks: Flow<Boolean> = dataStore.data.map { it[CONFIRM_LINKS] ?: true }

    override suspend fun setConfirmExternalLinks(confirm: Boolean) {
        dataStore.edit { it[CONFIRM_LINKS] = confirm }
    }

    override val appLock: Flow<AppLockSettings> =
        dataStore.data.map { prefs ->
            val defaults = AppLockSettings()
            AppLockSettings(
                enabled = prefs[APP_LOCK] ?: defaults.enabled,
                timeout = LockTimeout.entries.firstOrNull { it.name == prefs[LOCK_TIMEOUT] } ?: defaults.timeout,
                hideWidgetDetails = prefs[HIDE_WIDGET_DETAILS] ?: defaults.hideWidgetDetails,
            )
        }

    override suspend fun setAppLock(settings: AppLockSettings) {
        dataStore.edit {
            it[APP_LOCK] = settings.enabled
            it[LOCK_TIMEOUT] = settings.timeout.name
            it[HIDE_WIDGET_DETAILS] = settings.hideWidgetDetails
        }
    }

    companion object {
        private val CONFIRM_LINKS = booleanPreferencesKey("confirm_external_links")
        private val APP_LOCK = booleanPreferencesKey("app_lock")
        private val LOCK_TIMEOUT = stringPreferencesKey("app_lock_timeout")
        private val HIDE_WIDGET_DETAILS = booleanPreferencesKey("hide_widget_details")

        /** [path] must end in `.preferences_pb` and must not be shared with another DataStore. */
        fun create(path: String): DataStoreSettingsStore =
            DataStoreSettingsStore(PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() }))
    }
}
