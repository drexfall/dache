package dev.dache.core.dashboard

import dev.dache.core.model.DeployStatus
import dev.dache.core.model.MonitorStatus
import dev.dache.core.model.ProviderSnapshot
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Counts shown at the top of the overview and in the summary widget. */
data class DashboardSummary(
    val failingDeploys: Int,
    val downMonitors: Int,
    val expiringDomains: Int,
    /** Workflows whose latest run failed. */
    val failedCiRuns: Int,
) {
    val hasProblems: Boolean get() = failingDeploys + downMonitors + expiringDomains + failedCiRuns > 0

    companion object {
        val EXPIRY_WINDOW: Duration = 30.days

        fun from(
            snapshots: Collection<ProviderSnapshot>,
            now: Instant,
        ): DashboardSummary =
            DashboardSummary(
                failingDeploys =
                    snapshots.sumOf { s ->
                        s.sites.count { it.latestDeployment?.status == DeployStatus.FAILED }
                    },
                downMonitors = snapshots.sumOf { s -> s.monitors.count { it.status == MonitorStatus.DOWN } },
                expiringDomains =
                    snapshots.sumOf { s ->
                        s.domains.count { d -> d.expiresAt?.let { it - now <= EXPIRY_WINDOW } == true }
                    },
                failedCiRuns =
                    snapshots.sumOf { s ->
                        s.ciRuns.latestPerWorkflow().count {
                            it.status ==
                                DeployStatus.FAILED
                        }
                    },
            )
    }
}
