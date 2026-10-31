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
import dev.dache.app.resources.kind_service
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
import dev.dache.app.ui.components.Paged
import dev.dache.core.dashboard.DashboardState
import dev.dache.core.dashboard.latestPerWorkflow
import dev.dache.core.model.CiRun
import dev.dache.core.model.DeployStatus
import dev.dache.core.model.DnsRecord
import dev.dache.core.model.Domain
import dev.dache.core.model.Monitor
import dev.dache.core.model.MonitorStatus
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.Resource
import dev.dache.core.model.ResourceKind
import dev.dache.core.model.Server
import dev.dache.core.model.ServiceResource
import dev.dache.core.model.ServiceStatus
import dev.dache.core.model.Site
import dev.dache.core.model.Worker
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** One card per resource type present in [snapshots]; problems sort to the top of each. */
fun resourceSections(
    snapshots: List<ProviderSnapshot>,
    state: DashboardState,
): List<@Composable () -> Unit> = resourceSections(snapshots.flatMap { it.resources }, state)

/** One card per resource type among [resources]; problems sort to the top of each. */
fun resourceSections(
    resources: Collection<Resource>,
    state: DashboardState,
): List<@Composable () -> Unit> {
    val sites = resources.filterIsInstance<Site>().sortedBy { it.latestDeployment?.status.severity() }
    val runs = resources.filterIsInstance<CiRun>().latestPerWorkflow().sortedBy { it.status.severity() }
    val domains = resources.filterIsInstance<Domain>()
    val records = resources.filterIsInstance<DnsRecord>()
    val servers = resources.filterIsInstance<Server>()
    val workers = resources.filterIsInstance<Worker>()
    val monitors = resources.filterIsInstance<Monitor>().sortedBy { it.status != MonitorStatus.DOWN }
    val services = resources.filterIsInstance<ServiceResource>().sortedBy { it.status.severity() }
    return buildList {
        section(Res.string.section_sites, sites) { SiteRow(it, state) }
        section(Res.string.section_ci, runs) { CiRunRow(it, state) }
        section(Res.string.section_monitors, monitors) { MonitorRow(it, state) }
        section(Res.string.section_servers, servers) { ServerRow(it, state) }
        section(Res.string.section_workers, workers) { WorkerRow(it, state) }
        section(Res.string.section_domains, domains) { DomainRow(it, state) }
        section(Res.string.section_dns, records) { DnsRecordRow(it, state) }
        // Plugins name their own services, so these headings come from the manifest rather than resources.
        services.groupBy { it.service }.forEach { (service, items) ->
            add {
                SectionCard(items.first().serviceLabel, "service:$service", items.size) {
                    Paged(items, key = "service:$service") { ServiceResourceRow(it, state) }
                }
            }
        }
    }
}

/** The heading a kind's section, and a page of only that kind, goes by. */
fun ResourceKind.sectionTitle(): StringResource =
    when (this) {
        ResourceKind.SITE -> Res.string.section_sites
        ResourceKind.DOMAIN -> Res.string.section_domains
        ResourceKind.DNS_RECORD -> Res.string.section_dns
        ResourceKind.CI_RUN -> Res.string.section_ci
        ResourceKind.SERVER -> Res.string.section_servers
        ResourceKind.WORKER -> Res.string.section_workers
        ResourceKind.MONITOR -> Res.string.section_monitors
        ResourceKind.SERVICE -> Res.string.kind_service
    }

/** Adds a card for [items] unless there are none. */
private fun <T> MutableList<@Composable () -> Unit>.section(
    title: StringResource,
    items: List<T>,
    row: @Composable (T) -> Unit,
) {
    if (items.isNotEmpty()) {
        add { SectionCard(stringResource(title), title.key, items.size) { Paged(items, key = title.key) { row(it) } } }
    }
}

/**
 * Collapsible card of rows. Starts expanded; the header shows the count so a collapsed card still informs.
 * [key] keeps the expanded state apart per section.
 */
@Composable
private fun SectionCard(
    title: String,
    key: String,
    count: Int,
    rows: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(key) { mutableStateOf(true) }
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
                    stringResource(Res.string.section_count, title, count),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
            ExpandableBody(expanded) { Column { rows() } }
        }
    }
}

/** Down first, then degraded, then the rest. */
private fun ServiceStatus?.severity(): Int =
    when (this) {
        ServiceStatus.DOWN -> 0
        ServiceStatus.DEGRADED -> 1
        else -> 2
    }

/** Failures sort first, then in-progress work, then the rest. */
private fun DeployStatus?.severity(): Int =
    when (this) {
        DeployStatus.FAILED -> 0
        DeployStatus.BUILDING -> 1
        else -> 2
    }
