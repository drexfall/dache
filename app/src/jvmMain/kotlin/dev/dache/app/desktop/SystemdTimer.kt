package dev.dache.app.desktop

import dev.dache.core.alerts.AlertSettings
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Runs a command and returns its exit code with whatever it printed. */
fun interface CommandRunner {
    fun run(command: List<String>): Pair<Int, String>

    companion object {
        val System =
            CommandRunner { command ->
                try {
                    val process = ProcessBuilder(command).redirectErrorStream(true).start()
                    val output = process.inputStream.bufferedReader().readText()
                    if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) process.destroy()
                    process.exitValue() to output
                } catch (e: IOException) {
                    -1 to (e.message ?: "could not run ${command.first()}")
                }
            }

        private const val TIMEOUT_SECONDS = 30L
    }
}

/**
 * Background checks on Linux: a systemd user timer that runs `dache --check` every [AlertSettings.intervalMinutes].
 * [apply] writes and enables the units while checks are on, and disables and deletes them when they're off.
 * [launcher] is the command that starts Dache, from [currentLauncher].
 */
class SystemdTimer(
    private val unitDir: File = File(System.getProperty("user.home"), ".config/systemd/user"),
    private val launcher: List<String>,
    private val runner: CommandRunner = CommandRunner.System,
) {
    private val service = File(unitDir, "$NAME.service")
    private val timer = File(unitDir, "$NAME.timer")

    /** Null when the system agrees, else what went wrong, to show under the setting. */
    fun apply(settings: AlertSettings): String? =
        if (settings.enabled) install(settings.intervalMinutes) else uninstall()

    private fun install(minutes: Int): String? {
        unitDir.mkdirs()
        service.writeText(serviceUnit())
        timer.writeText(timerUnit(minutes))
        return systemctl("daemon-reload") ?: systemctl("enable", "--now", "$NAME.timer")
            ?: systemctl("restart", "$NAME.timer")
    }

    private fun uninstall(): String? {
        if (!timer.exists() && !service.exists()) return null
        // The timer may already be gone from systemd's view; deleting the files is what matters.
        systemctl("disable", "--now", "$NAME.timer")
        timer.delete()
        service.delete()
        return systemctl("daemon-reload")
    }

    private fun systemctl(vararg args: String): String? {
        val (code, output) = runner.run(listOf("systemctl", "--user") + args)
        return if (code == 0) null else output.trim().ifEmpty { "systemctl ${args.first()} failed ($code)" }
    }

    fun serviceUnit(): String =
        """
        |[Unit]
        |Description=Dache background check
        |After=network-online.target
        |
        |[Service]
        |Type=oneshot
        |ExecStart=${launcher.joinToString(" ") { quote(it) }} --check
        |
        """.trimMargin()

    fun timerUnit(minutes: Int): String =
        """
        |[Unit]
        |Description=Run Dache's background check every $minutes minutes
        |
        |[Timer]
        |OnBootSec=2min
        |OnUnitActiveSec=${minutes}min
        |Persistent=true
        |
        |[Install]
        |WantedBy=timers.target
        |
        """.trimMargin()

    companion object {
        const val NAME = "dache-check"

        /** systemd's quoting: wrap in double quotes, escape backslashes, quotes and `%` specifiers. */
        fun quote(arg: String): String =
            "\"" + arg.replace("\\", "\\\\").replace("\"", "\\\"").replace("%", "%%") + "\""

        /**
         * How to start this Dache again: the AppImage, the packaged launcher, or the running JVM with its class path.
         */
        fun currentLauncher(env: (String) -> String? = System::getenv): List<String> =
            env("APPIMAGE")?.let { listOf(it) }
                ?: System.getProperty("jpackage.app-path")?.let { listOf(it) }
                ?: listOf(
                    ProcessHandle
                        .current()
                        .info()
                        .command()
                        .orElse("java"),
                    "-cp",
                    System.getProperty("java.class.path"),
                    "dev.dache.app.MainKt",
                )
    }
}
