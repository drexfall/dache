package dev.dache.app.desktop

import dev.dache.core.alerts.AlertSettings
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SystemdTimerTest {
    private val dir = Files.createTempDirectory("dache-units").toFile()
    private val commands = mutableListOf<List<String>>()
    private var failWith: String? = null
    private val runner =
        CommandRunner { command ->
            commands += command
            failWith?.let { 1 to it } ?: (0 to "")
        }
    private val timer = SystemdTimer(dir, listOf("/opt/Dache 1.0/dache.AppImage"), runner)

    @Test
    fun turningChecksOnWritesAndEnablesTheTimer() {
        assertNull(timer.apply(AlertSettings(enabled = true, intervalMinutes = 30)))

        val service = dir.resolve("dache-check.service").readText()
        assertTrue("ExecStart=\"/opt/Dache 1.0/dache.AppImage\" --check" in service, service)
        assertTrue("OnUnitActiveSec=30min" in dir.resolve("dache-check.timer").readText())
        assertTrue(listOf("systemctl", "--user", "enable", "--now", "dache-check.timer") in commands)
    }

    @Test
    fun turningChecksOffRemovesTheUnits() {
        timer.apply(AlertSettings(enabled = true))
        commands.clear()

        assertNull(timer.apply(AlertSettings(enabled = false)))

        assertFalse(dir.resolve("dache-check.timer").exists())
        assertFalse(dir.resolve("dache-check.service").exists())
        assertEquals(listOf("systemctl", "--user", "disable", "--now", "dache-check.timer"), commands.first())
    }

    @Test
    fun offWithNothingInstalledTouchesNothing() {
        assertNull(timer.apply(AlertSettings(enabled = false)))
        assertTrue(commands.isEmpty())
    }

    @Test
    fun aSystemctlFailureIsReported() {
        failWith = "Failed to connect to bus"

        assertEquals("Failed to connect to bus", timer.apply(AlertSettings(enabled = true)))
    }

    @Test
    fun quotingEscapesSpecifiersAndQuotes() {
        assertEquals("\"a \\\"b\\\" 100%%\"", SystemdTimer.quote("a \"b\" 100%"))
    }

    @Test
    fun anAppImageLaunchesItself() {
        assertEquals(
            listOf("/x/Dache.AppImage"),
            SystemdTimer.currentLauncher {
                if (it ==
                    "APPIMAGE"
                ) {
                    "/x/Dache.AppImage"
                } else {
                    null
                }
            },
        )
    }
}
