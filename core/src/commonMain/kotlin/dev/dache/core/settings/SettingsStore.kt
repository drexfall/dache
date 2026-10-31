package dev.dache.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.dache.core.actions.WriteLog
import dev.dache.core.actions.WriteLogEntry
import dev.dache.core.alerts.AlertSettings
import dev.dache.core.alerts.AlertStore
import dev.dache.core.security.IntegritySignal
import dev.dache.core.security.IntegrityWarningSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
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
) : SettingsStore,
    AlertStore,
    WriteLog {
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

    override val alertSettings: Flow<AlertSettings> =
        dataStore.data.map { prefs ->
            val defaults = AlertSettings()
            AlertSettings(
                enabled = prefs[ALERTS] ?: defaults.enabled,
                intervalMinutes =
                    prefs[ALERT_INTERVAL]?.coerceAtLeast(AlertSettings.MIN_INTERVAL_MINUTES)
                        ?: defaults.intervalMinutes,
                deploys = prefs[ALERT_DEPLOYS] ?: defaults.deploys,
                monitors = prefs[ALERT_MONITORS] ?: defaults.monitors,
                ci = prefs[ALERT_CI] ?: defaults.ci,
            )
        }

    override suspend fun setAlertSettings(settings: AlertSettings) {
        dataStore.edit {
            it[ALERTS] = settings.enabled
            it[ALERT_INTERVAL] = settings.intervalMinutes
            it[ALERT_DEPLOYS] = settings.deploys
            it[ALERT_MONITORS] = settings.monitors
            it[ALERT_CI] = settings.ci
        }
    }

    override suspend fun reportedAlerts(): Set<String>? = dataStore.data.first()[REPORTED_ALERTS]

    override suspend fun setReportedAlerts(keys: Set<String>) {
        dataStore.edit { it[REPORTED_ALERTS] = keys }
    }

    override val entries: Flow<List<WriteLogEntry>> =
        dataStore.data.map { prefs -> prefs[WRITE_LOG]?.let { logJson.decodeFromString(logSerializer, it) }.orEmpty() }

    override suspend fun append(entry: WriteLogEntry) {
        dataStore.edit { prefs ->
            val current = prefs[WRITE_LOG]?.let { logJson.decodeFromString(logSerializer, it) }.orEmpty()
            prefs[WRITE_LOG] =
                logJson.encodeToString(logSerializer, (listOf(entry) + current).take(WriteLog.MAX_ENTRIES))
        }
    }

    companion object {
        private val WRITE_LOG = stringPreferencesKey("write_log")
        private val logJson = Json { ignoreUnknownKeys = true }
        private val logSerializer = ListSerializer(WriteLogEntry.serializer())
        private val ALERTS = booleanPreferencesKey("alerts")
        private val ALERT_INTERVAL = intPreferencesKey("alert_interval_minutes")
        private val ALERT_DEPLOYS = booleanPreferencesKey("alert_deploys")
        private val ALERT_MONITORS = booleanPreferencesKey("alert_monitors")
        private val ALERT_CI = booleanPreferencesKey("alert_ci")
        private val REPORTED_ALERTS = stringSetPreferencesKey("reported_alerts")
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
