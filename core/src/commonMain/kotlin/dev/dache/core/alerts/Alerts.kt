package dev.dache.core.alerts

import dev.dache.core.dashboard.latestPerWorkflow
import dev.dache.core.model.DeployStatus
import dev.dache.core.model.MonitorStatus
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.ResourceRef
import kotlinx.coroutines.flow.Flow

enum class AlertKind { DEPLOY_FAILED, MONITOR_DOWN, CI_FAILED }

/**
 * Something a background check found that wasn't there last time. [key] identifies the problem, not the check:
 * the same failed deploy keeps its key, so it alerts once. [title] and [body] are plain text for a notification;
 * [ref] is the resource to open.
 */
data class Alert(
    val key: String,
    val kind: AlertKind,
    val title: String,
    val body: String,
    val ref: ResourceRef,
)

/**
 * What background checks do. Off until the user turns them on, which is also when platforms ask for permission
 * to notify. [intervalMinutes] is at least [MIN_INTERVAL_MINUTES], the shortest period Android allows.
 */
data class AlertSettings(
    val enabled: Boolean = false,
    val intervalMinutes: Int = MIN_INTERVAL_MINUTES,
    val deploys: Boolean = true,
    val monitors: Boolean = true,
    val ci: Boolean = true,
) {
    fun allows(kind: AlertKind): Boolean =
        when (kind) {
            AlertKind.DEPLOY_FAILED -> deploys
            AlertKind.MONITOR_DOWN -> monitors
            AlertKind.CI_FAILED -> ci
        }

    companion object {
        const val MIN_INTERVAL_MINUTES = 15
        val INTERVALS = listOf(15, 30, 60, 180)
    }
}

/** Alert settings and the problems the last check already reported. */
interface AlertStore {
    val alertSettings: Flow<AlertSettings>

    suspend fun setAlertSettings(settings: AlertSettings)

    /** Keys reported so far, or null before the first check, which only records what is already wrong. */
    suspend fun reportedAlerts(): Set<String>?

    suspend fun setReportedAlerts(keys: Set<String>)
}

/**
 * Every problem in [snapshots] that can alert: a site's latest deploy failed, a monitor is down, or a workflow's
 * latest run failed.
 */
fun currentProblems(snapshots: Collection<ProviderSnapshot>): List<Alert> =
    snapshots.flatMap { snapshot ->
        val deploys =
            snapshot.sites.mapNotNull { site ->
                site.latestDeployment?.takeIf { it.status == DeployStatus.FAILED }?.let { deploy ->
                    Alert(
                        key = key(AlertKind.DEPLOY_FAILED, site.ref, deploy.nativeId),
                        kind = AlertKind.DEPLOY_FAILED,
                        title = "Deploy failed: ${site.name}",
                        body =
                            listOfNotNull(
                                site.providerId.displayName,
                                deploy.branch,
                                deploy.commitMessage,
                            ).joinToString(" · "),
                        ref = site.ref,
                    )
                }
            }
        val monitors =
            snapshot.monitors.filter { it.status == MonitorStatus.DOWN }.map { monitor ->
                Alert(
                    key = key(AlertKind.MONITOR_DOWN, monitor.ref, ""),
                    kind = AlertKind.MONITOR_DOWN,
                    title = "Down: ${monitor.name}",
                    body = listOf(monitor.providerId.displayName, monitor.url).joinToString(" · "),
                    ref = monitor.ref,
                )
            }
        val runs =
            snapshot.ciRuns.latestPerWorkflow().filter { it.status == DeployStatus.FAILED }.map { run ->
                Alert(
                    key = key(AlertKind.CI_FAILED, run.ref, run.nativeId),
                    kind = AlertKind.CI_FAILED,
                    title = "CI failed: ${run.displayName}",
                    body = listOf(run.providerId.displayName, run.branch).joinToString(" · "),
                    ref = run.ref,
                )
            }
        deploys + monitors + runs
    }

/** Kind, then the account, so keys of an account that failed to refresh can be told apart. */
private fun key(
    kind: AlertKind,
    ref: ResourceRef,
    extra: String,
): String = listOf(kind.name, ref.accountId, ref.providerId.id, ref.kind.name, ref.nativeId, extra).joinToString("|")

/** The account a key belongs to. */
internal fun accountOf(key: String): String = key.split('|').getOrElse(1) { "" }
