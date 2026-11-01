package dev.dache.app.desktop

import dev.dache.core.alerts.AlertSettings
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowsTaskSchedulerTest {
    private val commands = mutableListOf<List<String>>()
    private val written = mutableListOf<ByteArray>()
    private var queryExit = 0
    private val runner =
        CommandRunner { command ->
            commands += command
            if (command.getOrNull(1) ==
                "/Create"
            ) {
                written += java.io.File(command[command.indexOf("/XML") + 1]).readBytes()
            }
            (if (command.getOrNull(1) == "/Query") queryExit else 0) to ""
        }
    private val scheduler =
        WindowsTaskScheduler(
            listOf("C:\\Program Files\\Dache\\Dache.exe"),
            runner,
            Files.createTempDirectory("dache-task").toFile(),
        )

    @Test
    fun turningChecksOnRegistersATaskFromXml() {
        assertNull(scheduler.apply(AlertSettings(enabled = true, intervalMinutes = 30)))

        val create = commands.single()
        assertEquals(listOf("schtasks", "/Create", "/TN", "Dache\\Background check", "/XML"), create.take(5))
        val bytes = written.single()
        assertEquals(listOf(0xFF.toByte(), 0xFE.toByte()), bytes.take(2))
        val xml = bytes.drop(2).toByteArray().toString(Charsets.UTF_16LE)
        val document =
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                xml.byteInputStream(Charsets.UTF_16),
            )
        assertEquals("PT30M", document.getElementsByTagName("Interval").item(0).textContent)
        assertEquals(
            "C:\\Program Files\\Dache\\Dache.exe",
            document.getElementsByTagName("Command").item(0).textContent,
        )
        assertEquals("--check", document.getElementsByTagName("Arguments").item(0).textContent)
    }

    @Test
    fun aJvmLauncherKeepsItsClassPathAsOneArgument() {
        val xml =
            WindowsTaskScheduler(
                listOf("C:\\Java\\java.exe", "-cp", "C:\\My Apps\\a.jar", "dev.dache.app.MainKt"),
                runner,
            ).taskXml(15)

        assertTrue("<Arguments>-cp &quot;C:\\My Apps\\a.jar&quot; dev.dache.app.MainKt --check</Arguments>" in xml, xml)
    }

    @Test
    fun turningChecksOffDeletesOnlyAnExistingTask() {
        queryExit = 1
        assertNull(scheduler.apply(AlertSettings(enabled = false)))
        assertEquals(1, commands.size)

        queryExit = 0
        assertNull(scheduler.apply(AlertSettings(enabled = false)))
        assertEquals(listOf("schtasks", "/Delete", "/TN", "Dache\\Background check", "/F"), commands.last())
    }

    @Test
    fun windowsGetsItsOwnPlatform() {
        val env = mapOf("LOCALAPPDATA" to "C:\\Users\\me\\AppData\\Local")
        val windows = DesktopPlatform.current("Windows 11", env::get)

        assertIs<WindowsPlatform>(windows)
        assertTrue(windows.dataDir.path.endsWith("Dache"))
        assertIs<LinuxPlatform>(DesktopPlatform.current("Linux", env::get))
    }
}
