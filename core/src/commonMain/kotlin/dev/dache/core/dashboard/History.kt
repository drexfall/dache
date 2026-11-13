package dev.dache.core.dashboard

import dev.dache.core.model.CiRun
import dev.dache.core.model.DeployStatus
import dev.dache.core.model.ProviderSnapshot
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * A finished (or running) build at a point in time; deploys and CI runs both reduce to this. [key] is the
 * provider's id for the build, so the same build seen on two refreshes (building, then finished) is one event.
 */
@Serializable
data class StatusEvent(
    val at: Instant,
    val status: DeployStatus,
    /** What was built, e.g. the site or workflow name. */
    val title: String = "",
    /** Where the build lives in the provider's dashboard. */
    val webUrl: String? = null,
    val key: String = "",
)

/** Completed outcomes for one UTC day. */
data class DayCounts(
    val day: Instant,
    val succeeded: Int,
    val failed: Int,
    val cancelled: Int,
) {
    val total: Int get() = succeeded + failed + cancelled
}

private const val SECONDS_PER_DAY = 86_400L

/** Start of the UTC day containing [instant]. */
fun startOfUtcDay(instant: Instant): Instant =
    Instant.fromEpochSeconds(instant.epochSeconds.floorDiv(SECONDS_PER_DAY) * SECONDS_PER_DAY)

/**
 * Buckets [events] into the last [days] UTC days ending today, oldest first. Every day is present even when
 * empty, so charts keep an even time axis. Builds still running are left out.
 */
fun dailyStatusCounts(
    events: List<StatusEvent>,
    days: Int,
    now: Instant,
): List<DayCounts> {
    val today = startOfUtcDay(now)
    val first = today - (days - 1).days
    val byDay =
        events
            .filter { it.at >= first && it.at < today + 1.days }
            .groupBy { startOfUtcDay(it.at) }
    return (0 until days).map { offset ->
        val day = first + offset.days
        val dayEvents = byDay[day].orEmpty()
        DayCounts(
            day = day,
            succeeded = dayEvents.count { it.status == DeployStatus.SUCCESS },
            failed = dayEvents.count { it.status == DeployStatus.FAILED },
            cancelled = dayEvents.count { it.status == DeployStatus.CANCELLED },
        )
    }
}

fun ProviderSnapshot.deployEvents(): List<StatusEvent> =
    sites.flatMap { site ->
        site.deployments.map {
            StatusEvent(it.createdAt, it.status, site.name, it.webUrl, key = "${site.nativeId}/${it.nativeId}")
        }
    }

fun ProviderSnapshot.ciEvents(): List<StatusEvent> =
    ciRuns.map { StatusEvent(it.createdAt, it.status, it.displayName, it.webUrl, key = it.nativeId) }

/**
 * [live] plus every stored event it doesn't already have. The live build wins for a key both know, since it is
 * the newer word on that build; keyless events are kept apart by time and title.
 */
fun List<StatusEvent>.mergedWith(live: List<StatusEvent>): List<StatusEvent> {
    val byKey = LinkedHashMap<Any, StatusEvent>()
    (this + live).forEach { byKey[it.identity()] = it }
    return byKey.values.sortedBy { it.at }
}

internal fun StatusEvent.identity(): Any = key.ifEmpty { at to title }

/** Events that happened during the UTC day starting at [day], newest first. */
fun List<StatusEvent>.onUtcDay(day: Instant): List<StatusEvent> =
    filter { startOfUtcDay(it.at) == day }.sortedByDescending { it.at }

/** The newest run of each workflow in each repo: what "is CI green right now" means. */
fun List<CiRun>.latestPerWorkflow(): List<CiRun> =
    groupBy { it.repoUrl to it.workflowName }
        .values
        .map { runs -> runs.maxBy { it.createdAt } }
        .sortedByDescending { it.createdAt }

/** Median of [values], or null when empty. Uses the lower middle for even counts. */
fun median(values: List<Int>): Int? =
    values.sorted().let { sorted ->
        if (sorted.isEmpty()) null else sorted[(sorted.size - 1) / 2]
    }
