package dev.dache.app.desktop

import dev.dache.core.alerts.AlertSettings
import dev.dache.core.alerts.AlertStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HeadlessAlertStoreTest {
    @Test
    fun reportedKeysLiveInTheirOwnFile() =
        runTest {
            val file = Files.createTempDirectory("dache").toFile().resolve("reported-alerts")
            val app =
                object : AlertStore {
                    override val alertSettings = MutableStateFlow(AlertSettings())

                    override suspend fun setAlertSettings(settings: AlertSettings) = Unit

                    override suspend fun reportedAlerts(): Set<String>? =
                        error("the check must not read the app's copy")

                    override suspend fun setReportedAlerts(keys: Set<String>) = error("the check must not write it")
                }
            val store = HeadlessAlertStore(app, file)

            assertNull(store.reportedAlerts())
            store.setReportedAlerts(setOf("b", "a"))
            assertEquals(setOf("a", "b"), store.reportedAlerts())
            store.setReportedAlerts(emptySet())
            assertEquals(emptySet(), store.reportedAlerts())
        }
}
