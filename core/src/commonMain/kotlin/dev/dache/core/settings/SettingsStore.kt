package dev.dache.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.dache.core.security.IntegritySignal
import dev.dache.core.security.IntegrityWarningSettings
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

    val integrityWarning: Flow<IntegrityWarningSettings>

    suspend fun setIntegrityWarning(settings: IntegrityWarningSettings)
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

    override val integrityWarning: Flow<IntegrityWarningSettings> =
        dataStore.data.map { prefs ->
            IntegrityWarningSettings(
                seen =
                    prefs[INTEGRITY_SEEN]
                        .orEmpty()
                        .mapNotNull { name -> IntegritySignal.entries.firstOrNull { it.name == name } }
                        .toSet(),
                neverShow = prefs[INTEGRITY_NEVER_SHOW] ?: false,
            )
        }

    override suspend fun setIntegrityWarning(settings: IntegrityWarningSettings) {
        dataStore.edit {
            it[INTEGRITY_SEEN] = settings.seen.map { signal -> signal.name }.toSet()
            it[INTEGRITY_NEVER_SHOW] = settings.neverShow
        }
    }

    companion object {
        private val INTEGRITY_SEEN = stringSetPreferencesKey("integrity_warning_seen")
        private val INTEGRITY_NEVER_SHOW = booleanPreferencesKey("integrity_warning_never_show")
        private val CONFIRM_LINKS = booleanPreferencesKey("confirm_external_links")
        private val APP_LOCK = booleanPreferencesKey("app_lock")
        private val LOCK_TIMEOUT = stringPreferencesKey("app_lock_timeout")
        private val HIDE_WIDGET_DETAILS = booleanPreferencesKey("hide_widget_details")

        /** [path] must end in `.preferences_pb` and must not be shared with another DataStore. */
        fun create(path: String): DataStoreSettingsStore =
            DataStoreSettingsStore(PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() }))
    }
}
