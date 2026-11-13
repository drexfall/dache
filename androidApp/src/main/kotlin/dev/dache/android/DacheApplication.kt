package dev.dache.android

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.dache.android.alerts.AlertNotifier
import dev.dache.android.alerts.BackgroundChecks
import dev.dache.android.widget.DacheWidgets
import dev.dache.app.AppContainer
import dev.dache.app.AppStores
import dev.dache.core.account.DataStoreAccountStore
import dev.dache.core.dashboard.FileHistoryStore
import dev.dache.core.dashboard.FileSnapshotStore
import dev.dache.core.security.TinkTokenVault
import dev.dache.core.settings.DataStoreSettingsStore
import dev.dache.plugin.registry.DataStorePluginStore
import dev.dache.plugin.runtime.RequestLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File

class DacheApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val container: AppContainer by lazy {
        val settings = DataStoreSettingsStore.create(File(filesDir, "settings.preferences_pb").absolutePath)
        AppContainer(
            vault = TinkTokenVault(this),
            stores =
                AppStores(
                    accounts = DataStoreAccountStore.create(File(filesDir, "accounts.preferences_pb").absolutePath),
                    settings = settings,
                    alerts = settings,
                    writeLog = settings,
                    plugins = DataStorePluginStore.create(File(filesDir, "plugins.preferences_pb").absolutePath),
                    snapshots = FileSnapshotStore(FileSystem.SYSTEM, File(filesDir, "snapshots").toOkioPath()),
                    history = FileHistoryStore(FileSystem.SYSTEM, File(filesDir, "history").toOkioPath()),
                ),
            scope = appScope,
            // Request logging is debug-only; release builds never log requests.
            requestLogger = if (isDebuggable()) RequestLogger.Console else RequestLogger.None,
        )
    }

    private fun isDebuggable() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    override fun onCreate() {
        super.onCreate()
        // Process-wide start and stop: rotation and one activity replacing another don't count as leaving.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = container.appLock.onForeground()

                override fun onStop(owner: LifecycleOwner) = container.appLock.onBackground()
            },
        )
        AlertNotifier.createChannel(this)
        // Keep WorkManager's schedule in step with the background check settings.
        appScope.launch {
            container.alertStore.alertSettings
                .distinctUntilChanged()
                .collect { BackgroundChecks.schedule(this@DacheApplication, it) }
        }
        // Push fresh data to home-screen widgets whenever the dashboard changes.
        appScope.launch {
            container.dashboardRepository.state
                .map { it.lastUpdated to it.accounts }
                .distinctUntilChanged()
                .drop(1)
                .collect { DacheWidgets.updateAll(this@DacheApplication) }
        }
        // Redraw widgets when they start or stop hiding names, not only on the next refresh.
        appScope.launch {
            container.settingsStore.appLock
                .map { it.widgetDetailsHidden }
                .distinctUntilChanged()
                .drop(1)
                .collect { DacheWidgets.updateAll(this@DacheApplication) }
        }
    }
}
