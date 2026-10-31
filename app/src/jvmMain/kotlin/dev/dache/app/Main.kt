package dev.dache.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.dache.app.resources.Res
import dev.dache.app.resources.app_name
import dev.dache.core.account.DataStoreAccountStore
import dev.dache.core.security.SecretServiceTokenVault
import dev.dache.core.settings.DataStoreSettingsStore
import dev.dache.plugin.registry.DataStorePluginStore
import dev.dache.plugin.runtime.RequestLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.compose.resources.stringResource
import java.awt.Dimension
import java.io.File

fun main() {
    val dataDir = File(System.getenv("XDG_DATA_HOME") ?: "${System.getProperty("user.home")}/.local/share", "dache")
    dataDir.mkdirs()
    val container =
        AppContainer(
            vault = SecretServiceTokenVault(),
            accountStore = DataStoreAccountStore.create(File(dataDir, "accounts.preferences_pb").absolutePath),
            settingsStore = DataStoreSettingsStore.create(File(dataDir, "settings.preferences_pb").absolutePath),
            pluginStore = DataStorePluginStore.create(File(dataDir, "plugins.preferences_pb").absolutePath),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            // Off unless a developer opts in; requests are redacted either way.
            requestLogger = if (System.getenv("DACHE_LOG_HTTP") == "1") RequestLogger.Console else RequestLogger.None,
        )
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = stringResource(Res.string.app_name),
            state = rememberWindowState(width = 1320.dp, height = 860.dp),
            // Desktop convention: F5 or Ctrl/Cmd+R refreshes.
            onPreviewKeyEvent = { event ->
                val refresh =
                    event.type == KeyEventType.KeyDown &&
                        (event.key == Key.F5 || (event.key == Key.R && (event.isCtrlPressed || event.isMetaPressed)))
                if (refresh) container.refreshInBackground()
                refresh
            },
        ) {
            window.minimumSize = Dimension(MIN_WIDTH, MIN_HEIGHT)
            DacheApp(container)
        }
    }
}

private const val MIN_WIDTH = 400
private const val MIN_HEIGHT = 600
