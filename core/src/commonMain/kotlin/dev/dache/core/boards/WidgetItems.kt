package dev.dache.core.boards

import dev.dache.core.dashboard.DashboardSummary
import dev.dache.core.model.DeployStatus
import dev.dache.core.model.Domain
import dev.dache.core.model.Monitor
import dev.dache.core.model.MonitorStatus
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.Server
import dev.dache.core.model.ServerStatus
import dev.dache.core.model.Site
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Problems first: critical, then warning, then the rest. */
private val severityOrder = listOf(ItemTone.CRITICAL, ItemTone.WARNING, ItemTone.GOOD, ItemTone.NEUTRAL)

private val problems = setOf(ItemTone.CRITICAL, ItemTone.WARNING)

/** The resources a [DatapointShape.SET] datapoint is about, problems first, then by name. */
internal fun widgetItems(
    datapoint: Datapoint,
    snapshots: List<ProviderSnapshot>,
    now: Instant,
): List<WidgetItem> {
    val items =
        when (datapoint) {
            Datapoint.SITES -> snapshots.flatMap { it.sites }.map(::siteItem)
            Datapoint.FAILING_SITES ->
                snapshots.flatMap { it.sites }.map(::siteItem).filter { it.tone == ItemTone.CRITICAL }
            Datapoint.DOMAINS -> snapshots.flatMap { it.domains }.map { domainItem(it, now) }
            Datapoint.EXPIRING_DOMAINS ->
                snapshots.flatMap { it.domains }.map { domainItem(it, now) }.filter { it.tone in problems }
            Datapoint.SERVERS -> snapshots.flatMap { it.servers }.map(::serverItem)
            Datapoint.WORKERS -> snapshots.flatMap { it.workers }.map { WidgetItem(it.name, ItemTone.NEUTRAL, it.ref) }
            Datapoint.MONITORS -> snapshots.flatMap { it.monitors }.map(::monitorItem)
            Datapoint.MONITORS_DOWN ->
                snapshots.flatMap { it.monitors }.map(::monitorItem).filter { it.tone == ItemTone.CRITICAL }
            else -> error("$datapoint is not a set")
        }
    return items.sortedWith(compareBy<WidgetItem> { severityOrder.indexOf(it.tone) }.thenBy { it.name.lowercase() })
}

private fun siteItem(site: Site): WidgetItem {
    val status = site.latestDeployment?.status ?: DeployStatus.UNKNOWN
    val tone =
        when (status) {
            DeployStatus.SUCCESS -> ItemTone.GOOD
            DeployStatus.FAILED -> ItemTone.CRITICAL
            DeployStatus.BUILDING -> ItemTone.WARNING
            DeployStatus.CANCELLED, DeployStatus.UNKNOWN -> ItemTone.NEUTRAL
        }
    return WidgetItem(site.name, tone, site.ref, ItemDetail.Deploy(status))
}

private fun domainItem(
    domain: Domain,
    now: Instant,
): WidgetItem {
    val left = domain.expiresAt?.let { it - now }
    val tone =
        when {
            left == null -> ItemTone.NEUTRAL
            left <= 0.days -> ItemTone.CRITICAL
            left <= DashboardSummary.EXPIRY_WINDOW -> ItemTone.WARNING
            else -> ItemTone.GOOD
        }
    return WidgetItem(domain.name, tone, domain.ref, domain.expiresAt?.let(ItemDetail::Expires))
}

private fun serverItem(server: Server): WidgetItem {
    val tone =
        when (server.status) {
            ServerStatus.ACTIVE -> ItemTone.GOOD
            ServerStatus.OFF -> ItemTone.CRITICAL
            ServerStatus.PROVISIONING -> ItemTone.WARNING
            ServerStatus.UNKNOWN -> ItemTone.NEUTRAL
        }
    return WidgetItem(server.name, tone, server.ref, ItemDetail.Server(server.status))
}

private fun monitorItem(monitor: Monitor): WidgetItem {
    val tone =
        when (monitor.status) {
            MonitorStatus.UP -> ItemTone.GOOD
            MonitorStatus.DOWN -> ItemTone.CRITICAL
            MonitorStatus.PAUSED, MonitorStatus.UNKNOWN -> ItemTone.NEUTRAL
        }
    return WidgetItem(monitor.name, tone, monitor.ref, ItemDetail.Monitor(monitor.status, monitor.uptimePercent))
}
