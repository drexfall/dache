package dev.dache.android

import android.app.Application
import dev.dache.android.widget.DacheWidgets
import dev.dache.app.AppContainer
import dev.dache.core.account.DataStoreAccountStore
import dev.dache.core.security.TinkTokenVault
import dev.dache.core.settings.DataStoreSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

class DacheApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val container: AppContainer by lazy {
        AppContainer(
            vault = TinkTokenVault(this),
            accountStore = DataStoreAccountStore.create(File(filesDir, "accounts.preferences_pb").absolutePath),
            settingsStore = DataStoreSettingsStore.create(File(filesDir, "settings.preferences_pb").absolutePath),
            scope = appScope,
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Push fresh data to home-screen widgets whenever the dashboard changes.
        appScope.launch {
            container.dashboardRepository.state
                .map { it.lastUpdated to it.accounts }
                .distinctUntilChanged()
                .drop(1)
                .collect { DacheWidgets.updateAll(this@DacheApplication) }
        }
    }
}
