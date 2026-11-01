package dev.dache.app.desktop

import dev.dache.core.alerts.AlertSettings
import java.io.File

/**
 * Background checks on Windows: a Task Scheduler task that runs `Dache.exe --check` every
 * [AlertSettings.intervalMinutes] for the signed-in user, only while there's a network. The task is
 * registered from XML, which keeps paths with spaces intact where `schtasks /TR` quoting wouldn't.
 */
class WindowsTaskScheduler(
    private val launcher: List<String>,
    private val runner: CommandRunner = CommandRunner.System,
    private val tempDir: File = File(System.getProperty("java.io.tmpdir")),
) : BackgroundScheduler {
    override fun apply(settings: AlertSettings): String? =
        if (settings.enabled) install(settings.intervalMinutes) else uninstall()

    private fun install(minutes: Int): String? {
        val file = File.createTempFile("dache-task", ".xml", tempDir)
        return try {
            // schtasks reads task XML as UTF-16, as the declaration says.
            file.writeBytes(byteArrayOf(BOM_FIRST, BOM_SECOND) + taskXml(minutes).toByteArray(Charsets.UTF_16LE))
            schtasks("/Create", "/TN", NAME, "/XML", file.absolutePath, "/F")
        } finally {
            file.delete()
        }
    }

    private fun uninstall(): String? =
        if (runner.run(listOf("schtasks", "/Query", "/TN", NAME)).first !=
            0
        ) {
            null
        } else {
            schtasks("/Delete", "/TN", NAME, "/F")
        }

    private fun schtasks(vararg args: String): String? {
        val (code, output) = runner.run(listOf("schtasks") + args)
        return if (code == 0) null else output.trim().ifEmpty { "schtasks ${args.first()} failed ($code)" }
    }

    fun taskXml(minutes: Int): String {
        val command = escape(launcher.first())
        val arguments = escape((launcher.drop(1).map(::quote) + "--check").joinToString(" "))
        return """
            |<?xml version="1.0" encoding="UTF-16"?>
            |<Task version="1.2" xmlns="http://schemas.microsoft.com/windows/2004/02/mit/task">
            |  <RegistrationInfo><Description>Dache background check</Description></RegistrationInfo>
            |  <Triggers>
            |    <TimeTrigger>
            |      <StartBoundary>2026-01-01T00:00:00</StartBoundary>
            |      <Repetition><Interval>PT${minutes}M</Interval><StopAtDurationEnd>false</StopAtDurationEnd></Repetition>
            |      <Enabled>true</Enabled>
            |    </TimeTrigger>
            |  </Triggers>
            |  <Principals>
            |    <Principal id="Author"><LogonType>InteractiveToken</LogonType><RunLevel>LeastPrivilege</RunLevel></Principal>
            |  </Principals>
            |  <Settings>
            |    <MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>
            |    <DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries>
            |    <StopIfGoingOnBatteries>false</StopIfGoingOnBatteries>
            |    <RunOnlyIfNetworkAvailable>true</RunOnlyIfNetworkAvailable>
            |    <StartWhenAvailable>true</StartWhenAvailable>
            |    <ExecutionTimeLimit>PT10M</ExecutionTimeLimit>
            |    <Enabled>true</Enabled>
            |  </Settings>
            |  <Actions Context="Author">
            |    <Exec><Command>$command</Command><Arguments>$arguments</Arguments></Exec>
            |  </Actions>
            |</Task>
            |
            """.trimMargin()
    }

    override val name: String = NAME

    companion object {
        const val NAME = "Dache\\Background check"
        private const val BOM_FIRST: Byte = 0xFF.toByte()
        private const val BOM_SECOND: Byte = 0xFE.toByte()

        private fun quote(arg: String): String =
            if (' ' in arg ||
                '"' in arg
            ) {
                "\"" + arg.replace("\"", "\\\"") + "\""
            } else {
                arg
            }

        private fun escape(text: String): String =
            text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
    }
}
