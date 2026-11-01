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
import dev.dache.app.desktop.DesktopNotifier
import dev.dache.app.desktop.DesktopPlatform
import dev.dache.app.desktop.HeadlessAlertStore
import dev.dache.app.resources.Res
import dev.dache.app.resources.alerts_timer_failed
import dev.dache.app.resources.app_name
import dev.dache.core.account.DataStoreAccountStore
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

/** `--check` runs one background check without a window, for the system's scheduler; otherwise the app starts. */
fun main(args: Array<String>) {
    val headless = "--check" in args
    val platform = DesktopPlatform.current()
    val dataDir = platform.dataDir
    dataDir.mkdirs()
    val settings = DataStoreSettingsStore.create(File(dataDir, "settings.preferences_pb").absolutePath)
    val container =
        AppContainer(
            vault = platform.vault,
            stores =
                AppStores(
                    accounts = DataStoreAccountStore.create(File(dataDir, "accounts.preferences_pb").absolutePath),
                    settings = settings,
                    alerts = if (headless) HeadlessAlertStore(settings, File(dataDir, "reported-alerts")) else settings,
                    writeLog = settings,
                    plugins = DataStorePluginStore.create(File(dataDir, "plugins.preferences_pb").absolutePath),
                ),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            // Off unless a developer opts in; requests are redacted either way.
            requestLogger = if (System.getenv("DACHE_LOG_HTTP") == "1") RequestLogger.Console else RequestLogger.None,
        )
    if (headless) exitProcess(runCheck(container, platform.notifier))
    val timerProblem = MutableStateFlow<String?>(null)
    // Keep the system's schedule in step with the setting, and its launcher pointing at this copy of Dache.
    container.scope.launch(Dispatchers.IO) {
        container.alertStore.alertSettings
            .distinctUntilChanged()
            .collect { timerProblem.value = platform.scheduler.apply(it) }
    }
    val backgroundChecks =
        BackgroundChecksPlatform(platform.notifier) {
            val problem by timerProblem.collectAsState()
            TimerNote(problem, platform)
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
private fun runCheck(
    container: AppContainer,
    notifier: DesktopNotifier,
): Int =
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
        notifier.show(alerts, hideDetails)
        0
    }

@Composable
private fun TimerNote(
    problem: String?,
    platform: DesktopPlatform,
) {
    Text(
        problem?.let { stringResource(Res.string.alerts_timer_failed, it) }
            ?: stringResource(platform.scheduleNote, platform.scheduler.name),
        style = MaterialTheme.typography.bodySmall,
        color = if (problem == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
    )
}
