package dev.dache.app.desktop

import dev.dache.app.resources.Res
import dev.dache.app.resources.alerts_timer
import dev.dache.app.resources.alerts_timer_windows
import dev.dache.core.alerts.AlertSettings
import dev.dache.core.security.DpapiTokenVault
import dev.dache.core.security.SecretServiceTokenVault
import dev.dache.core.security.TokenVault
import org.jetbrains.compose.resources.StringResource
import java.io.File

/** Keeps the operating system's schedule for background checks in step with the setting. */
interface BackgroundScheduler {
    /** What the system calls the schedule, for the note under the setting. */
    val name: String

    /** Null when the system agrees, else what went wrong. */
    fun apply(settings: AlertSettings): String?
}

/** What differs between desktop systems: where data and tokens live, how checks are scheduled and alerts shown. */
interface DesktopPlatform {
    val dataDir: File
    val vault: TokenVault
    val notifier: DesktopNotifier
    val scheduler: BackgroundScheduler

    /** Describes [scheduler] under the background checks setting; takes its name. */
    val scheduleNote: StringResource

    companion object {
        fun current(
            os: String = System.getProperty("os.name"),
            env: (String) -> String? = System::getenv,
        ): DesktopPlatform = if (os.startsWith("Windows")) WindowsPlatform(env) else LinuxPlatform(env)
    }
}

/** Linux: XDG data directory, the Secret Service keyring, a systemd user timer and notify-send. */
class LinuxPlatform(
    env: (String) -> String?,
) : DesktopPlatform {
    override val dataDir = File(env("XDG_DATA_HOME") ?: "${System.getProperty("user.home")}/.local/share", "dache")
    override val vault: TokenVault by lazy { SecretServiceTokenVault() }
    override val notifier: DesktopNotifier by lazy { NotifySendNotifier() }
    override val scheduler: BackgroundScheduler by lazy { SystemdTimer(launcher = SystemdTimer.currentLauncher(env)) }
    override val scheduleNote = Res.string.alerts_timer
}

/** Windows: %LOCALAPPDATA%\Dache, DPAPI-encrypted tokens, a Task Scheduler task and tray notifications. */
class WindowsPlatform(
    env: (String) -> String?,
) : DesktopPlatform {
    override val dataDir = File(env("LOCALAPPDATA") ?: "${System.getProperty("user.home")}\\AppData\\Local", "Dache")
    override val vault: TokenVault by lazy { DpapiTokenVault(File(dataDir, "tokens")) }
    override val notifier: DesktopNotifier by lazy { TrayNotifier() }
    override val scheduler: BackgroundScheduler by lazy { WindowsTaskScheduler(SystemdTimer.currentLauncher(env)) }
    override val scheduleNote = Res.string.alerts_timer_windows
}
