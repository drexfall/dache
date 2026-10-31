package dev.dache.app.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.action_collapse
import dev.dache.app.resources.action_expand
import dev.dache.app.resources.domain_expires_in
import dev.dache.app.resources.domain_no_expiry
import dev.dache.app.resources.linked_to
import dev.dache.app.resources.monitor_response_time
import dev.dache.app.resources.monitor_uptime
import dev.dache.app.resources.state_collapsed
import dev.dache.app.resources.state_expanded
import dev.dache.app.resources.status_expiring
import dev.dache.app.ui.components.ExpandChevron
import dev.dache.app.ui.components.ExpandableBody
import dev.dache.app.ui.components.StatusChip
import dev.dache.app.ui.components.StatusTone
import dev.dache.app.ui.components.relativeTime
import dev.dache.core.dashboard.DashboardState
import dev.dache.core.dashboard.DashboardSummary
import dev.dache.core.model.CertificateStatus
import dev.dache.core.model.CiRun
import dev.dache.core.model.DnsRecord
import dev.dache.core.model.Domain
import dev.dache.core.model.Monitor
import dev.dache.core.model.Resource
import dev.dache.core.model.Server
import dev.dache.core.model.Site
import dev.dache.core.model.Worker
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

internal const val SEPARATOR = " · "

@Composable
fun SiteRow(
    site: Site,
    state: DashboardState,
) {
    val deploy = site.latestDeployment
    ResourceRow(
        resource = site,
        state = state,
        title = site.name,
        summary =
            listOfNotNull(
                listOfNotNull(site.providerId.displayName, deploy?.branch, deploy?.let { relativeTime(it.createdAt) })
                    .joinToString(SEPARATOR),
                deploy?.commitMessage,
            ),
        trailing = { deploy?.let { StatusChip(it.status) } },
    ) { SiteDetails(site) }
}

@Composable
fun CiRunRow(
    run: CiRun,
    state: DashboardState,
) {
    ResourceRow(
        resource = run,
        state = state,
        title = run.displayName,
        summary = listOf(listOf(run.branch, relativeTime(run.createdAt)).joinToString(SEPARATOR)),
        trailing = { StatusChip(run.status) },
    ) { CiRunDetails(run, state) }
}

@Composable
fun DomainRow(
    domain: Domain,
    state: DashboardState,
) {
    val now = Clock.System.now()
    val expiresAt = domain.expiresAt
    val expiry = domainExpiry(domain, now)
    val expiring = expiresAt != null && expiresAt - now <= DashboardSummary.EXPIRY_WINDOW
    val brokenCertificate =
        domain.certificates.firstOrNull {
            it.status == CertificateStatus.FAILED ||
                it.status == CertificateStatus.EXPIRED
        }
    ResourceRow(
        resource = domain,
        state = state,
        title = domain.name,
        summary = listOf("${domain.providerId.displayName}$SEPARATOR$expiry"),
        trailing = {
            when {
                brokenCertificate != null -> StatusChip(brokenCertificate.status)
                expiring -> StatusChip(StatusTone.UNKNOWN, Res.string.status_expiring)
            }
        },
    ) { DomainDetails(domain, expiry) }
}

@Composable
fun domainExpiry(
    domain: Domain,
    now: Instant,
): String {
    val expiresAt = domain.expiresAt ?: return stringResource(Res.string.domain_no_expiry)
    return stringResource(Res.string.domain_expires_in, (expiresAt - now).inWholeDays.toInt())
}

@Composable
fun DnsRecordRow(
    record: DnsRecord,
    state: DashboardState,
) {
    ResourceRow(
        resource = record,
        state = state,
        title = "${record.type}  ${record.name}",
        summary = listOf("→ ${record.content}", record.providerId.displayName),
    ) { DnsRecordDetails(record) }
}

@Composable
fun ServerRow(
    server: Server,
    state: DashboardState,
) {
    ResourceRow(
        resource = server,
        state = state,
        title = server.name,
        summary =
            listOf(
                listOfNotNull(server.providerId.displayName, server.region, server.publicIpv4).joinToString(SEPARATOR),
            ),
        trailing = { StatusChip(server.status) },
    ) { ServerDetails(server) }
}

@Composable
fun WorkerRow(
    worker: Worker,
    state: DashboardState,
) {
    ResourceRow(
        resource = worker,
        state = state,
        title = worker.name,
        summary =
            listOf(
                listOfNotNull(worker.providerId.displayName, worker.modifiedAt?.let { relativeTime(it) })
                    .joinToString(SEPARATOR),
            ) + worker.routes.take(1),
    ) { WorkerDetails(worker) }
}

@Composable
fun MonitorRow(
    monitor: Monitor,
    state: DashboardState,
) {
    val stats =
        listOfNotNull(
            monitor.uptimePercent?.let { stringResource(Res.string.monitor_uptime, "$it%") },
            monitor.responseTimeMs?.let { stringResource(Res.string.monitor_response_time, it) },
        )
    ResourceRow(
        resource = monitor,
        state = state,
        title = monitor.name,
        summary = listOf(monitor.url, stats.joinToString(SEPARATOR)).filter { it.isNotEmpty() },
        trailing = { StatusChip(monitor.status) },
    ) { MonitorDetails(monitor) }
}

/**
 * One list row. Tapping it expands details in place; it never leaves the app. Leaving is always an explicit,
 * icon-marked button inside the details.
 */
@Composable
internal fun ResourceRow(
    resource: Resource,
    state: DashboardState,
    title: String,
    summary: List<String>,
    trailing: @Composable () -> Unit = {},
    details: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(resource.ref.toString()) { mutableStateOf(false) }
    val linkedProviders =
        state
            .linkedTo(resource.ref)
            .map { it.providerId }
            .filter { it != resource.providerId }
            .distinct()
            .map { it.displayName }
    val stateText = stringResource(if (expanded) Res.string.state_expanded else Res.string.state_collapsed)
    val actionText = stringResource(if (expanded) Res.string.action_collapse else Res.string.action_expand)
    Column {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier =
                Modifier
                    .clickable(role = Role.Button, onClickLabel = actionText) { expanded = !expanded }
                    .semantics { stateDescription = stateText },
            headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Column {
                    summary.forEach { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    if (linkedProviders.isNotEmpty() && !expanded) {
                        Text(
                            stringResource(Res.string.linked_to, linkedProviders.joinToString(", ")),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    trailing()
                    ExpandChevron(expanded)
                }
            },
        )
        ExpandableBody(expanded) {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                details()
                LinkedResources(resource, state)
                OpenInProviderButton(resource)
            }
        }
    }
}
