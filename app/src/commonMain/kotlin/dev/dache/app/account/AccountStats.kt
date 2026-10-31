package dev.dache.app.account

import androidx.compose.runtime.Composable
import dev.dache.app.dashboard.HISTORY_DAYS
import dev.dache.app.dashboard.Stat
import dev.dache.app.dashboard.StatDetail
import dev.dache.app.resources.Res
import dev.dache.app.resources.stat_account_balance
import dev.dache.app.resources.stat_avg_per_day
import dev.dache.app.resources.stat_avg_response
import dev.dache.app.resources.stat_deploys
import dev.dache.app.resources.stat_dns_records
import dev.dache.app.resources.stat_domains
import dev.dache.app.resources.stat_downtime_days
import dev.dache.app.resources.stat_failed_14d
import dev.dache.app.resources.stat_last_14_days_total
import dev.dache.app.resources.stat_median_build
import dev.dache.app.resources.stat_median_run
import dev.dache.app.resources.stat_monitors_up
import dev.dache.app.resources.stat_month_to_date
import dev.dache.app.resources.stat_of_total
import dev.dache.app.resources.stat_runs
import dev.dache.app.resources.stat_servers_running
import dev.dache.app.resources.stat_sites
import dev.dache.app.resources.stat_workers
import dev.dache.app.resources.stat_workflows
import dev.dache.app.ui.components.ValueFormats
import dev.dache.app.ui.components.deployStatusLabel
import dev.dache.app.ui.components.monitorStatusLabel
import dev.dache.app.ui.components.rememberValueFormats
import dev.dache.app.ui.components.serverStatusLabel
import dev.dache.app.ui.components.serviceStatusLabel
import dev.dache.core.dashboard.StatusEvent
import dev.dache.core.dashboard.dailyStatusCounts
import dev.dache.core.dashboard.latestPerWorkflow
import dev.dache.core.dashboard.median
import dev.dache.core.model.MonitorStatus
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.ServerStatus
import dev.dache.core.model.ServiceStatus
import dev.dache.core.model.TimedValue
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/** Headline numbers for whatever kinds of resources this account has. Provider-agnostic. */
@Composable
fun accountStats(
    snapshot: ProviderSnapshot,
    now: Instant,
): List<Stat> {
    val formats = rememberValueFormats()
    return buildList {
        if (snapshot.sites.isNotEmpty()) addAll(siteStats(snapshot, now, formats))
        if (snapshot.ciRuns.isNotEmpty()) addAll(ciStats(snapshot, now, formats))
        if (snapshot.servers.isNotEmpty()) add(serverStat(snapshot))
        snapshot.billing?.let { billing ->
            val money = { value: Double -> formats.money(value, billing.currency) }
            val spend = spendDetails(billing.dailyCost, money)
            add(Stat(stringResource(Res.string.stat_month_to_date), money(billing.monthToDateUsage), details = spend))
            add(Stat(stringResource(Res.string.stat_account_balance), money(billing.accountBalance), details = spend))
        }
        if (snapshot.monitors.isNotEmpty()) addAll(monitorStats(snapshot, formats))
        if (snapshot.workers.isNotEmpty()) {
            add(
                Stat(
                    stringResource(Res.string.stat_workers),
                    snapshot.workers.size.toString(),
                    details = snapshot.workers.map { StatDetail(it.name, it.routes.firstOrNull()) },
                ),
            )
        }
        if (snapshot.domains.isNotEmpty()) {
            add(
                Stat(
                    stringResource(Res.string.stat_domains),
                    snapshot.domains.size.toString(),
                    details = snapshot.domains.map { StatDetail(it.name) },
                ),
            )
        }
        addAll(serviceStats(snapshot))
        if (snapshot.dnsRecords.isNotEmpty()) {
            add(
                Stat(
                    stringResource(Res.string.stat_dns_records),
                    snapshot.dnsRecords.size.toString(),
                    details =
                        snapshot.dnsRecords.groupBy { it.type }.map { (type, records) ->
                            StatDetail(type, records.size.toString())
                        },
                ),
            )
        }
    }
}

/** Builds and failures per group over the last [HISTORY_DAYS] days. */
private data class GroupCounts(
    val label: String,
    val total: Int,
    val failed: Int,
)

private fun groupCounts(
    events: Map<String, List<StatusEvent>>,
    now: Instant,
): List<GroupCounts> =
    events.map { (label, list) ->
        val days = dailyStatusCounts(list, HISTORY_DAYS, now)
        GroupCounts(label, days.sumOf { it.total }, days.sumOf { it.failed })
    }

private fun List<GroupCounts>.totalDetails() = map { StatDetail(it.label, it.total.toString()) }

private fun List<GroupCounts>.failedDetails() =
    filter {
        it.failed > 0
    }.map { StatDetail(it.label, it.failed.toString()) }

@Composable
private fun siteStats(
    snapshot: ProviderSnapshot,
    now: Instant,
    formats: ValueFormats,
): List<Stat> {
    val bySite =
        snapshot.sites.associate { site ->
            site.name to
                site.deployments.map { StatusEvent(it.createdAt, it.status) }
        }
    val counts = groupCounts(bySite, now)
    val failedTotal = counts.sumOf { it.failed }
    val medians =
        snapshot.sites.mapNotNull { site ->
            median(site.deployments.mapNotNull { it.durationSeconds })?.let { site.name to it }
        }
    return listOfNotNull(
        Stat(
            stringResource(Res.string.stat_sites),
            snapshot.sites.size.toString(),
            details = snapshot.sites.map { StatDetail(it.name, deployStatusLabel(it.latestDeployment?.status)) },
        ),
        Stat(
            stringResource(Res.string.stat_deploys),
            counts.sumOf { it.total }.toString(),
            details = counts.totalDetails(),
        ),
        Stat(
            stringResource(Res.string.stat_failed_14d),
            failedTotal.toString(),
            problem = failedTotal > 0,
            details = counts.failedDetails(),
        ),
        median(snapshot.sites.flatMap { s -> s.deployments.mapNotNull { it.durationSeconds } })?.let { overall ->
            Stat(
                stringResource(Res.string.stat_median_build),
                formats.duration(overall.toDouble()),
                details = medians.map { (name, seconds) -> StatDetail(name, formats.duration(seconds.toDouble())) },
            )
        },
    )
}

@Composable
private fun ciStats(
    snapshot: ProviderSnapshot,
    now: Instant,
    formats: ValueFormats,
): List<Stat> {
    val byWorkflow = snapshot.ciRuns.groupBy { it.displayName }
    val events = byWorkflow.mapValues { (_, runs) -> runs.map { StatusEvent(it.createdAt, it.status) } }
    val counts = groupCounts(events, now)
    val failedTotal = counts.sumOf { it.failed }
    return listOfNotNull(
        Stat(
            stringResource(Res.string.stat_workflows),
            byWorkflow.size.toString(),
            details =
                snapshot.ciRuns.latestPerWorkflow().map {
                    StatDetail(
                        it.displayName,
                        deployStatusLabel(it.status),
                    )
                },
        ),
        Stat(
            stringResource(Res.string.stat_runs),
            counts.sumOf { it.total }.toString(),
            details = counts.totalDetails(),
        ),
        Stat(
            stringResource(Res.string.stat_failed_14d),
            failedTotal.toString(),
            problem = failedTotal > 0,
            details = counts.failedDetails(),
        ),
        median(snapshot.ciRuns.mapNotNull { it.durationSeconds })?.let { overall ->
            Stat(
                stringResource(Res.string.stat_median_run),
                formats.duration(overall.toDouble()),
                details =
                    byWorkflow.mapNotNull { (name, runs) ->
                        median(
                            runs.mapNotNull { it.durationSeconds },
                        )?.let { StatDetail(name, formats.duration(it.toDouble())) }
                    },
            )
        },
    )
}

@Composable
private fun serverStat(snapshot: ProviderSnapshot): Stat =
    Stat(
        label = stringResource(Res.string.stat_servers_running),
        value = snapshot.servers.count { it.status == ServerStatus.ACTIVE }.toString(),
        caption = stringResource(Res.string.stat_of_total, snapshot.servers.size),
        details = snapshot.servers.map { StatDetail(it.name, serverStatusLabel(it.status)) },
    )

/** The last days' total and daily average, when the provider gives daily costs at all. */
@Composable
private fun spendDetails(
    dailyCost: List<TimedValue>,
    money: (Double) -> String,
): List<StatDetail> {
    if (dailyCost.isEmpty()) return emptyList()
    val total = dailyCost.sumOf { it.value }
    return listOf(
        StatDetail(stringResource(Res.string.stat_last_14_days_total), money(total)),
        StatDetail(stringResource(Res.string.stat_avg_per_day), money(total / dailyCost.size)),
    )
}

/** One count per plugin-described service, named as the plugin names it. */
@Composable
private fun serviceStats(snapshot: ProviderSnapshot): List<Stat> =
    snapshot.services.groupBy { it.serviceLabel }.map { (label, items) ->
        Stat(
            label = label,
            value = items.size.toString(),
            problem = items.any { it.status == ServiceStatus.DOWN },
            details =
                items.map {
                    StatDetail(
                        it.name,
                        it.status?.let { status -> serviceStatusLabel(status) } ?: it.detail,
                    )
                },
        )
    }

@Composable
private fun monitorStats(
    snapshot: ProviderSnapshot,
    formats: ValueFormats,
): List<Stat> {
    val monitors = snapshot.monitors
    val latest = monitors.mapNotNull { m -> m.responseTimeMs?.let { m.name to it } }
    val downtime = monitors.map { m -> m.name to m.dailyUptime.count { it.value < FULL_UPTIME } }
    val downtimeTotal = downtime.sumOf { it.second }
    return listOfNotNull(
        Stat(
            label = stringResource(Res.string.stat_monitors_up),
            value = monitors.count { it.status == MonitorStatus.UP }.toString(),
            caption = stringResource(Res.string.stat_of_total, monitors.size),
            problem = monitors.any { it.status == MonitorStatus.DOWN },
            details = monitors.map { StatDetail(it.name, monitorStatusLabel(it.status)) },
        ),
        latest.takeIf { it.isNotEmpty() }?.let {
            Stat(
                stringResource(Res.string.stat_avg_response),
                formats.milliseconds(it.map { (_, ms) -> ms }.average()),
                details = it.map { (name, ms) -> StatDetail(name, formats.milliseconds(ms.toDouble())) },
            )
        },
        Stat(
            stringResource(Res.string.stat_downtime_days),
            downtimeTotal.toString(),
            problem = downtimeTotal > 0,
            details = downtime.map { (name, days) -> StatDetail(name, days.toString()) },
        ),
    )
}

private const val FULL_UPTIME = 100.0
