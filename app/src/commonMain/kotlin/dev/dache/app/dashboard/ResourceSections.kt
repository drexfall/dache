package dev.dache.app.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.section_ci
import dev.dache.app.resources.section_count
import dev.dache.app.resources.section_dns
import dev.dache.app.resources.section_domains
import dev.dache.app.resources.section_monitors
import dev.dache.app.resources.section_servers
import dev.dache.app.resources.section_sites
import dev.dache.app.resources.section_workers
import dev.dache.app.ui.components.ExpandableBody
import dev.dache.app.ui.components.ExpandableHeader
import dev.dache.core.dashboard.DashboardState
import dev.dache.core.dashboard.latestPerWorkflow
import dev.dache.core.model.DeployStatus
import dev.dache.core.model.MonitorStatus
import dev.dache.core.model.ProviderSnapshot
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** One card per resource type present in [snapshots]; problems sort to the top of each. */
fun resourceSections(
    snapshots: List<ProviderSnapshot>,
    state: DashboardState,
): List<@Composable () -> Unit> {
    val sites = snapshots.flatMap { it.sites }.sortedBy { it.latestDeployment?.status.severity() }
    val runs = snapshots.flatMap { it.ciRuns }.latestPerWorkflow().sortedBy { it.status.severity() }
    val domains = snapshots.flatMap { it.domains }
    val records = snapshots.flatMap { it.dnsRecords }
    val servers = snapshots.flatMap { it.servers }
    val workers = snapshots.flatMap { it.workers }
    val monitors = snapshots.flatMap { it.monitors }.sortedBy { it.status != MonitorStatus.DOWN }
    return buildList {
        section(Res.string.section_sites, sites) { SiteRow(it, state) }
        section(Res.string.section_ci, runs) { CiRunRow(it, state) }
        section(Res.string.section_monitors, monitors) { MonitorRow(it, state) }
        section(Res.string.section_servers, servers) { ServerRow(it, state) }
        section(Res.string.section_workers, workers) { WorkerRow(it, state) }
        section(Res.string.section_domains, domains) { DomainRow(it, state) }
        section(Res.string.section_dns, records) { DnsRecordRow(it, state) }
    }
}

/** Adds a card for [items] unless there are none. */
private fun <T> MutableList<@Composable () -> Unit>.section(
    title: StringResource,
    items: List<T>,
    row: @Composable (T) -> Unit,
) {
    if (items.isNotEmpty()) add { SectionCard(title, items.size) { items.forEach { row(it) } } }
}

/** Collapsible card of rows. Starts expanded; the header shows the count so a collapsed card still informs. */
@Composable
private fun SectionCard(
    title: StringResource,
    count: Int,
    rows: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(title.key) { mutableStateOf(true) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            ExpandableHeader(
                expanded = expanded,
                onToggle = { expanded = !expanded },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Text(
                    stringResource(Res.string.section_count, stringResource(title), count),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
            ExpandableBody(expanded) { Column { rows() } }
        }
    }
}

/** Failures sort first, then in-progress work, then the rest. */
private fun DeployStatus?.severity(): Int =
    when (this) {
        DeployStatus.FAILED -> 0
        DeployStatus.BUILDING -> 1
        else -> 2
    }
