package dev.dache.app.dashboard

import androidx.compose.runtime.Composable
import dev.dache.app.dashboard.charts.BuildGroup
import dev.dache.app.dashboard.charts.CostCard
import dev.dache.app.dashboard.charts.DurationBarsCard
import dev.dache.app.dashboard.charts.OutcomeHistoryCard
import dev.dache.app.dashboard.charts.ResponseTimeCard
import dev.dache.app.dashboard.charts.UptimeCard
import dev.dache.app.resources.Res
import dev.dache.app.resources.chart_build_time_subtitle
import dev.dache.app.resources.chart_build_time_title
import dev.dache.app.resources.chart_ci_description
import dev.dache.app.resources.chart_ci_title
import dev.dache.app.resources.chart_deploys_description
import dev.dache.app.resources.chart_deploys_title
import dev.dache.app.resources.chart_run_time_subtitle
import dev.dache.app.resources.chart_run_time_title
import dev.dache.core.dashboard.AccountHistory
import dev.dache.core.dashboard.StatusEvent
import dev.dache.core.dashboard.ciEvents
import dev.dache.core.dashboard.deployEvents
import dev.dache.core.dashboard.mergedWith
import dev.dache.core.dashboard.withHistory
import dev.dache.core.model.ProviderSnapshot
import kotlin.time.Instant

const val HISTORY_DAYS = 14

/**
 * Every chart card the given data supports, in a stable order. Pages pass in one account's snapshot or all of
 * them, with the matching [histories], so the outcome and monitor charts reach back past what the provider lists
 * right now; nothing here knows which provider the data came from.
 */
fun activityCharts(
    snapshots: List<ProviderSnapshot>,
    histories: List<AccountHistory>,
    now: Instant,
): List<@Composable () -> Unit> {
    val deployEvents = histories.flatMap { it.deploys }.mergedWith(snapshots.flatMap { it.deployEvents() })
    val ciEvents = histories.flatMap { it.ciRuns }.mergedWith(snapshots.flatMap { it.ciEvents() })
    val monitors =
        snapshots.flatMap { snapshot ->
            snapshot.monitors.map { monitor ->
                monitor.withHistory(histories.firstNotNullOfOrNull { it.monitor(monitor.nativeId) })
            }
        }
    val buildGroups = buildGroups(snapshots)
    val runGroups = runGroups(snapshots)

    return buildList {
        if (deployEvents.isNotEmpty()) {
            add {
                OutcomeHistoryCard(
                    Res.string.chart_deploys_title,
                    Res.string.chart_deploys_description,
                    deployEvents,
                    now,
                )
            }
        }
        if (ciEvents.isNotEmpty()) {
            add { OutcomeHistoryCard(Res.string.chart_ci_title, Res.string.chart_ci_description, ciEvents, now) }
        }
        if (buildGroups.isNotEmpty()) {
            add {
                DurationBarsCard(
                    Res.string.chart_build_time_title,
                    Res.string.chart_build_time_subtitle,
                    buildGroups,
                )
            }
        }
        if (runGroups.isNotEmpty()) {
            add { DurationBarsCard(Res.string.chart_run_time_title, Res.string.chart_run_time_subtitle, runGroups) }
        }
        if (monitors.any { it.dailyUptime.isNotEmpty() }) add { UptimeCard(monitors) }
        if (monitors.any { it.responseTimes.isNotEmpty() }) add { ResponseTimeCard(monitors) }
        // Some providers give spend as totals only; with no daily history there is nothing to chart.
        snapshots.mapNotNull { it.billing }.filter { it.dailyCost.isNotEmpty() }.forEach { add { CostCard(it) } }
    }
}

/** Each site's deploys with their build times. */
private fun buildGroups(snapshots: List<ProviderSnapshot>): List<BuildGroup> =
    snapshots
        .flatMap { it.sites }
        .map { site ->
            val builds =
                site.deployments.mapNotNull { d ->
                    d.durationSeconds?.let { seconds ->
                        StatusEvent(d.createdAt, d.status, site.name, d.webUrl) to
                            seconds
                    }
                }
            BuildGroup(site.name, builds)
        }.filter { it.builds.isNotEmpty() }

/** Each workflow's runs with their durations. */
private fun runGroups(snapshots: List<ProviderSnapshot>): List<BuildGroup> =
    snapshots
        .flatMap { it.ciRuns }
        .groupBy { it.displayName }
        .map { (label, runs) ->
            val builds =
                runs.mapNotNull { r ->
                    r.durationSeconds?.let { seconds -> StatusEvent(r.createdAt, r.status, label, r.webUrl) to seconds }
                }
            BuildGroup(label, builds)
        }.filter { it.builds.isNotEmpty() }
