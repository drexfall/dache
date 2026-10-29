package dev.dache.app.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dache.app.dashboard.charts.EventList
import dev.dache.app.resources.Res
import dev.dache.app.resources.detail_branch
import dev.dache.app.resources.detail_certificate
import dev.dache.app.resources.detail_certificates
import dev.dache.app.resources.detail_commit
import dev.dache.app.resources.detail_content
import dev.dache.app.resources.detail_domains
import dev.dache.app.resources.detail_duration
import dev.dache.app.resources.detail_expires
import dev.dache.app.resources.detail_ip
import dev.dache.app.resources.detail_modified
import dev.dache.app.resources.detail_name
import dev.dache.app.resources.detail_no_routes
import dev.dache.app.resources.detail_recent_deploys
import dev.dache.app.resources.detail_recent_runs
import dev.dache.app.resources.detail_region
import dev.dache.app.resources.detail_repo
import dev.dache.app.resources.detail_response_24h
import dev.dache.app.resources.detail_response_range
import dev.dache.app.resources.detail_routes
import dev.dache.app.resources.detail_started
import dev.dache.app.resources.detail_type
import dev.dache.app.resources.detail_uptime_30d
import dev.dache.app.resources.detail_url
import dev.dache.app.resources.detail_zone
import dev.dache.app.resources.link_open_repo
import dev.dache.app.resources.link_visit
import dev.dache.app.ui.components.ExternalLinkIconButton
import dev.dache.app.ui.components.certificateStatusLabel
import dev.dache.app.ui.components.formatDay
import dev.dache.app.ui.components.formatHour
import dev.dache.app.ui.components.rememberValueFormats
import dev.dache.core.dashboard.DashboardState
import dev.dache.core.dashboard.StatusEvent
import dev.dache.core.model.CertificateStatus
import dev.dache.core.model.CiRun
import dev.dache.core.model.DnsRecord
import dev.dache.core.model.Domain
import dev.dache.core.model.Monitor
import dev.dache.core.model.Server
import dev.dache.core.model.Site
import dev.dache.core.model.Worker
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

private const val RECENT_COUNT = 5

/** Label/value pair in an expanded row; [linkUrl] adds an icon button that leaves the app. */
@Composable
fun DetailLine(
    label: String,
    value: String,
    linkUrl: String? = null,
    linkLabel: String? = null,
) {
    Row(Modifier.heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(LABEL_WEIGHT),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, modifier = Modifier.weight(VALUE_WEIGHT), style = MaterialTheme.typography.bodyMedium)
        // Rows without a link keep the icon's width so values line up.
        if (linkUrl != null) ExternalLinkIconButton(linkUrl, linkLabel ?: value) else Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun DetailHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

private fun dateTime(instant: Instant): String = "${formatDay(instant)} ${formatHour(instant)}"

@Composable
fun SiteDetails(site: Site) {
    val formats = rememberValueFormats()
    val latest = site.latestDeployment
    Column {
        DetailLine(
            stringResource(Res.string.detail_url),
            site.primaryUrl,
            site.primaryUrl,
            stringResource(Res.string.link_visit, site.name),
        )
        if (site.customDomains.isNotEmpty()) {
            DetailLine(stringResource(Res.string.detail_domains), site.customDomains.joinToString(", "))
        }
        site.repoUrl?.let { repo ->
            val browsable = repo.takeIf { it.startsWith("https://") }
            DetailLine(
                stringResource(Res.string.detail_repo),
                repo,
                browsable,
                stringResource(Res.string.link_open_repo),
            )
        }
        latest?.branch?.let { DetailLine(stringResource(Res.string.detail_branch), it) }
        latest?.commitMessage?.let { DetailLine(stringResource(Res.string.detail_commit), it) }
        latest?.durationSeconds?.let {
            DetailLine(
                stringResource(Res.string.detail_duration),
                formats.duration(it.toDouble()),
            )
        }
    }
    val recent = site.deployments.sortedByDescending { it.createdAt }.take(RECENT_COUNT)
    if (recent.isNotEmpty()) {
        DetailHeading(stringResource(Res.string.detail_recent_deploys))
        EventList(
            events =
                recent.map {
                    StatusEvent(
                        it.createdAt,
                        it.status,
                        it.commitMessage ?: it.branch ?: site.name,
                        it.webUrl,
                    )
                },
            whenText = { dateTime(it.at) },
        )
    }
}

@Composable
fun CiRunDetails(
    run: CiRun,
    state: DashboardState,
) {
    val formats = rememberValueFormats()
    Column {
        DetailLine(
            stringResource(Res.string.detail_repo),
            run.repoFullName,
            run.repoUrl,
            stringResource(Res.string.link_open_repo),
        )
        DetailLine(stringResource(Res.string.detail_branch), run.branch)
        DetailLine(stringResource(Res.string.detail_started), dateTime(run.createdAt))
        run.durationSeconds?.let {
            DetailLine(
                stringResource(Res.string.detail_duration),
                formats.duration(it.toDouble()),
            )
        }
    }
    val recent =
        state.snapshots
            .flatMap { it.ciRuns }
            .filter { it.repoUrl == run.repoUrl && it.workflowName == run.workflowName }
            .sortedByDescending { it.createdAt }
            .take(RECENT_COUNT)
    DetailHeading(stringResource(Res.string.detail_recent_runs))
    EventList(
        events = recent.map { StatusEvent(it.createdAt, it.status, it.branch, it.webUrl) },
        whenText = { dateTime(it.at) },
    )
}

@Composable
fun DomainDetails(
    domain: Domain,
    expiry: String,
) {
    Column {
        DetailLine(stringResource(Res.string.detail_name), domain.name)
        DetailLine(
            stringResource(Res.string.detail_expires),
            domain.expiresAt?.let { "${formatDay(it)} · $expiry" } ?: expiry,
        )
        val certificates = domain.certificates.filter { it.status != CertificateStatus.INACTIVE }
        if (certificates.isNotEmpty()) {
            DetailHeading(stringResource(Res.string.detail_certificates))
            certificates.forEach { certificate ->
                val status = certificateStatusLabel(certificate.status)
                DetailLine(
                    certificate.hosts.joinToString(", ").ifEmpty { domain.name },
                    certificate.expiresAt?.let { stringResource(Res.string.detail_certificate, status, formatDay(it)) }
                        ?: status,
                )
            }
        }
    }
}

@Composable
fun DnsRecordDetails(record: DnsRecord) {
    Column {
        DetailLine(stringResource(Res.string.detail_type), record.type)
        DetailLine(stringResource(Res.string.detail_name), record.name)
        DetailLine(stringResource(Res.string.detail_content), record.content)
        DetailLine(stringResource(Res.string.detail_zone), record.zoneName)
    }
}

@Composable
fun ServerDetails(server: Server) {
    Column {
        DetailLine(stringResource(Res.string.detail_region), server.region)
        server.publicIpv4?.let { DetailLine(stringResource(Res.string.detail_ip), it) }
    }
}

@Composable
fun WorkerDetails(worker: Worker) {
    Column {
        DetailLine(
            stringResource(Res.string.detail_routes),
            worker.routes.joinToString("\n").ifEmpty { stringResource(Res.string.detail_no_routes) },
        )
        worker.modifiedAt?.let { DetailLine(stringResource(Res.string.detail_modified), dateTime(it)) }
    }
}

@Composable
fun MonitorDetails(monitor: Monitor) {
    val formats = rememberValueFormats()
    Column {
        DetailLine(
            stringResource(Res.string.detail_url),
            monitor.url,
            monitor.url,
            stringResource(Res.string.link_visit, monitor.name),
        )
        if (monitor.dailyUptime.isNotEmpty()) {
            DetailLine(
                stringResource(Res.string.detail_uptime_30d),
                formats.percent(
                    monitor.dailyUptime
                        .map {
                            it.value
                        }.average(),
                ),
            )
        }
        if (monitor.responseTimes.isNotEmpty()) {
            val values = monitor.responseTimes.map { it.value }
            DetailLine(
                stringResource(Res.string.detail_response_24h),
                stringResource(
                    Res.string.detail_response_range,
                    formats.milliseconds(values.min()),
                    formats.milliseconds(values.average()),
                    formats.milliseconds(values.max()),
                ),
            )
        }
    }
}

private const val LABEL_WEIGHT = 0.35f
private const val VALUE_WEIGHT = 0.65f
