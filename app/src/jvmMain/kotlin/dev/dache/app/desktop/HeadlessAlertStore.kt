package dev.dache.app.desktop

import dev.dache.core.alerts.AlertSettings
import dev.dache.core.alerts.AlertStore
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * The headless check's alert state. Settings come from the app's store, which only the app writes; the problems
 * already reported live in their own file, which only the check writes, so the two processes never write the
 * same DataStore.
 */
class HeadlessAlertStore(
    private val settings: AlertStore,
    private val file: File,
) : AlertStore {
    override val alertSettings: Flow<AlertSettings> get() = settings.alertSettings

    override suspend fun setAlertSettings(settings: AlertSettings) = this.settings.setAlertSettings(settings)

    override suspend fun reportedAlerts(): Set<String>? =
        file
            .takeIf { it.isFile }
            ?.readLines()
            ?.filter { it.isNotBlank() }
            ?.toSet()

    override suspend fun setReportedAlerts(keys: Set<String>) {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(keys.sorted().joinToString("\n"))
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
    }
}
