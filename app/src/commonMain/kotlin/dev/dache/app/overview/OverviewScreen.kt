package dev.dache.app.overview

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.dache.app.dashboard.CollapsibleSection
import dev.dache.app.dashboard.ProviderErrorCard
import dev.dache.app.dashboard.Stat
import dev.dache.app.dashboard.StatDetail
import dev.dache.app.dashboard.StatTiles
import dev.dache.app.dashboard.activityCharts
import dev.dache.app.dashboard.resourceSections
import dev.dache.app.navigation.LocalAppNavigator
import dev.dache.app.resources.Res
import dev.dache.app.resources.action_connect_account
import dev.dache.app.resources.domain_expires_in
import dev.dache.app.resources.overview_activity
import dev.dache.app.resources.overview_empty_body
import dev.dache.app.resources.overview_empty_title
import dev.dache.app.resources.overview_resources
import dev.dache.app.resources.overview_title
import dev.dache.app.resources.summary_down_monitors
import dev.dache.app.resources.summary_expiring_domains
import dev.dache.app.resources.summary_failed_ci
import dev.dache.app.resources.summary_failing_deploys
import dev.dache.app.status.StatusBanner
import dev.dache.app.ui.components.EmptyState
import dev.dache.app.ui.layout.AdaptiveGrid
import dev.dache.app.ui.layout.PageScaffold
import dev.dache.core.dashboard.DashboardState
import dev.dache.core.dashboard.DashboardSummary
import dev.dache.core.dashboard.latestPerWorkflow
import dev.dache.core.model.DeployStatus
import dev.dache.core.model.MonitorStatus
import dev.dache.core.model.Resource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
fun OverviewScreen(
    state: DashboardState,
    onRefresh: () -> Unit,
    onOpenMenu: (() -> Unit)?,
    onConnectAccount: () -> Unit,
) {
    PageScaffold(
        title = stringResource(Res.string.overview_title),
        lastUpdated = state.lastUpdated,
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        onOpenMenu = onOpenMenu,
    ) {
        if (state.accounts.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Home,
                title = stringResource(Res.string.overview_empty_title),
                body = stringResource(Res.string.overview_empty_body),
            ) {
                Button(onClick = onConnectAccount) { Text(stringResource(Res.string.action_connect_account)) }
            }
            return@PageScaffold
        }
        val now = state.lastUpdated ?: Clock.System.now()
        StatTiles(problemStats(state, now))
        val navigator = LocalAppNavigator.current
        StatusBanner(state.statuses.values) { status ->
            state.accounts.firstOrNull { it.providerId == status.providerId }?.let { navigator.openAccount(it.id) }
        }
        state.results.filter { it.error != null }.forEach { ProviderErrorCard(it, onRefresh) }

        val charts = activityCharts(state.snapshots, now)
        if (charts.isNotEmpty()) {
            CollapsibleSection(Res.string.overview_activity) { AdaptiveGrid(charts, minCellWidth = 420.dp) }
        }
        val sections = resourceSections(state.snapshots, state)
        if (sections.isNotEmpty()) {
            CollapsibleSection(Res.string.overview_resources) { AdaptiveGrid(sections, minCellWidth = 420.dp) }
        }
    }
}

/** The four problem counters; each expands to the items behind it, which open their account page. */
@Composable
private fun problemStats(
    state: DashboardState,
    now: Instant,
): List<Stat> {
    val navigator = LocalAppNavigator.current
    val snapshots = state.snapshots

    fun detail(
        resource: Resource,
        value: String? = resource.providerId.displayName,
    ) = StatDetail(resource.displayName, value) { navigator.openAccount(resource.accountId) }

    val failingSites = snapshots.flatMap { it.sites }.filter { it.latestDeployment?.status == DeployStatus.FAILED }
    val downMonitors = snapshots.flatMap { it.monitors }.filter { it.status == MonitorStatus.DOWN }
    val expiring =
        snapshots.flatMap { it.domains }.filter { d ->
            d.expiresAt?.let { it - now <= DashboardSummary.EXPIRY_WINDOW } == true
        }
    val failedRuns = snapshots.flatMap { it.ciRuns }.latestPerWorkflow().filter { it.status == DeployStatus.FAILED }
    return listOf(
        problemStat(Res.string.summary_failing_deploys, failingSites.map { detail(it) }),
        problemStat(Res.string.summary_down_monitors, downMonitors.map { detail(it) }),
        problemStat(
            Res.string.summary_expiring_domains,
            expiring.map { domain ->
                val days = domain.expiresAt?.let { (it - now).inWholeDays.toInt() } ?: 0
                detail(domain, stringResource(Res.string.domain_expires_in, days))
            },
        ),
        problemStat(Res.string.summary_failed_ci, failedRuns.map { detail(it) }),
    )
}

@Composable
private fun problemStat(
    label: StringResource,
    details: List<StatDetail>,
) = Stat(
    label = stringResource(label),
    value = details.size.toString(),
    problem = details.isNotEmpty(),
    details = details,
)
