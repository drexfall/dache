package dev.dache.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import dev.dache.app.alerts.BackgroundChecksPlatform
import dev.dache.app.desktop.DesktopNotifications
import dev.dache.app.desktop.HeadlessAlertStore
import dev.dache.app.desktop.SystemdTimer
import dev.dache.app.resources.Res
import dev.dache.app.resources.alerts_timer
import dev.dache.app.resources.alerts_timer_failed
import dev.dache.app.resources.app_name
import dev.dache.core.account.DataStoreAccountStore
import dev.dache.core.security.SecretServiceTokenVault
import dev.dache.core.settings.DataStoreSettingsStore
import dev.dache.plugin.registry.DataStorePluginStore
import dev.dache.plugin.runtime.RequestLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.stringResource
import java.awt.Dimension
import java.io.File
import kotlin.system.exitProcess

/** `--check` runs one background check without a window, for the systemd timer; otherwise the app starts. */
fun main(args: Array<String>) {
    val headless = "--check" in args
    val dataDir = File(System.getenv("XDG_DATA_HOME") ?: "${System.getProperty("user.home")}/.local/share", "dache")
    dataDir.mkdirs()
    val settings = DataStoreSettingsStore.create(File(dataDir, "settings.preferences_pb").absolutePath)
    val container =
        AppContainer(
            vault = SecretServiceTokenVault(),
            accountStore = DataStoreAccountStore.create(File(dataDir, "accounts.preferences_pb").absolutePath),
            settingsStore = settings,
            alertStore = if (headless) HeadlessAlertStore(settings, File(dataDir, "reported-alerts")) else settings,
            pluginStore = DataStorePluginStore.create(File(dataDir, "plugins.preferences_pb").absolutePath),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            // Off unless a developer opts in; requests are redacted either way.
            requestLogger = if (System.getenv("DACHE_LOG_HTTP") == "1") RequestLogger.Console else RequestLogger.None,
        )
    if (headless) exitProcess(runCheck(container))
    val timer = SystemdTimer(launcher = SystemdTimer.currentLauncher())
    val timerProblem = MutableStateFlow<String?>(null)
    // Keep the systemd timer in step with the setting, and its launcher pointing at this copy of Dache.
    container.scope.launch(Dispatchers.IO) {
        container.alertStore.alertSettings
            .distinctUntilChanged()
            .collect { timerProblem.value = timer.apply(it) }
    }
    val notifications = DesktopNotifications()
    val backgroundChecks =
        BackgroundChecksPlatform(notifications) {
            val problem by timerProblem.collectAsState()
            TimerNote(problem)
        }
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
            DacheApp(container, backgroundChecks = backgroundChecks)
        }
    }
}

private const val MIN_WIDTH = 400
private const val MIN_HEIGHT = 600

/** One check: notifies about anything new and returns the exit code. Does nothing when checks were turned off. */
private fun runCheck(container: AppContainer): Int =
    runBlocking {
        if (!container.alertStore.alertSettings
                .first()
                .enabled
        ) {
            return@runBlocking 0
        }
        val alerts = container.backgroundCheck.run()
        val hideDetails =
            container.settingsStore.appLock
                .first()
                .widgetDetailsHidden
        DesktopNotifications().show(alerts, hideDetails)
        0
    }

@Composable
private fun TimerNote(problem: String?) {
    Text(
        problem?.let { stringResource(Res.string.alerts_timer_failed, it) }
            ?: stringResource(Res.string.alerts_timer, SystemdTimer.NAME),
        style = MaterialTheme.typography.bodySmall,
        color = if (problem == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
    )
}
