package dev.dache.core.alerts

import dev.dache.core.dashboard.DashboardRepository
import kotlinx.coroutines.flow.first

/**
 * One background check: refresh every account, then report problems that weren't there last time. The first
 * check only records what is already wrong, so turning checks on doesn't replay old failures. A problem that
 * clears is forgotten and alerts again if it comes back; an account that fails to refresh keeps its problems,
 * since nothing is known about them.
 */
class BackgroundCheck(
    private val dashboard: DashboardRepository,
    private val store: AlertStore,
) {
    suspend fun run(): List<Alert> {
        val settings = store.alertSettings.first()
        val state = dashboard.refreshAndGet()
        val failing =
            state.results
                .filter { it.error != null }
                .map { it.account.id }
                .toSet()
        val current = currentProblems(state.snapshots)
        val reported = store.reportedAlerts()
        val unknown = reported.orEmpty().filter { accountOf(it) in failing }
        store.setReportedAlerts(current.map { it.key }.toSet() + unknown)
        return if (reported == null) {
            emptyList()
        } else {
            current.filter { it.key !in reported && settings.allows(it.kind) }
        }
    }
}
