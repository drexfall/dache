package dev.dache.core.dashboard

import dev.dache.core.model.Monitor
import dev.dache.core.model.MonitorStatus
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.ResourceKind
import dev.dache.core.model.ServicePage
import dev.dache.core.model.TimedValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.serialization.Serializable
import okio.FileSystem
import okio.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** One monitor's status as seen by one refresh. */
@Serializable
data class MonitorSample(
    val at: Instant,
    val status: MonitorStatus,
    val responseTimeMs: Int?,
)

/**
 * What a monitor has shown over time: the provider's own series, merged across refreshes, and a sample per
 * refresh for the days the provider doesn't report.
 */
@Serializable
data class MonitorHistory(
    val nativeId: String,
    val responseTimes: List<TimedValue> = emptyList(),
    val dailyUptime: List<TimedValue> = emptyList(),
    val samples: List<MonitorSample> = emptyList(),
)

/**
 * What one account's refreshes have shown over the last [HistoryStore.RETENTION]. Unlike the snapshot cache,
 * which is replaced every refresh, this only grows (and is trimmed by age), so charts can reach back further than
 * the provider lists. Deploys and CI runs are keyed by the provider's id; monitor points by time bucket.
 */
@Serializable
data class AccountHistory(
    val accountId: String,
    val deploys: List<StatusEvent> = emptyList(),
    val ciRuns: List<StatusEvent> = emptyList(),
    val monitors: List<MonitorHistory> = emptyList(),
) {
    /** This history plus what [snapshot] shows, trimmed to the retention window ending at [now]. */
    fun absorb(
        snapshot: ProviderSnapshot,
        now: Instant,
    ): AccountHistory {
        val cutoff = now - HistoryStore.RETENTION
        val live = snapshot.monitors.associateBy { it.nativeId }
        val known = monitors.associateBy { it.nativeId }
        val ids = (known.keys + live.keys)
        return AccountHistory(
            accountId = accountId,
            deploys = deploys.mergedWith(snapshot.deployEvents()).filter { it.at >= cutoff },
            ciRuns = ciRuns.mergedWith(snapshot.ciEvents()).filter { it.at >= cutoff },
            monitors =
                ids.map { id ->
                    val history = known[id] ?: MonitorHistory(id)
                    live[id]?.let { history.absorb(it, now) }?.trimmed(cutoff) ?: history.trimmed(cutoff)
                },
        )
    }

    /** Only the history of what [page] shows, like [ProviderSnapshot.only]. */
    fun only(page: ServicePage): AccountHistory =
        copy(
            deploys = if (ResourceKind.SITE in page.kinds) deploys else emptyList(),
            ciRuns = if (ResourceKind.CI_RUN in page.kinds) ciRuns else emptyList(),
            monitors = if (ResourceKind.MONITOR in page.kinds) monitors else emptyList(),
        )

    fun monitor(nativeId: String): MonitorHistory? = monitors.firstOrNull { it.nativeId == nativeId }
}

private fun MonitorHistory.absorb(
    monitor: Monitor,
    now: Instant,
): MonitorHistory =
    copy(
        responseTimes = responseTimes.mergedWith(monitor.responseTimes, HistoryStore.RESPONSE_BUCKET),
        dailyUptime = dailyUptime.mergedWith(monitor.dailyUptime, 1.days),
        samples =
            (samples + MonitorSample(now, monitor.status, monitor.responseTimeMs))
                .associateBy { bucket(it.at, HistoryStore.SAMPLE_BUCKET) }
                .values
                .sortedBy { it.at },
    )

private fun MonitorHistory.trimmed(cutoff: Instant): MonitorHistory =
    copy(
        responseTimes = responseTimes.filter { it.at >= cutoff },
        dailyUptime = dailyUptime.filter { it.at >= cutoff },
        samples = samples.filter { it.at >= cutoff },
    )

/** [live] plus every stored point in a bucket it doesn't cover; the live value wins where both have one. */
fun List<TimedValue>.mergedWith(
    live: List<TimedValue>,
    bucket: Duration,
): List<TimedValue> {
    val byBucket = LinkedHashMap<Long, TimedValue>()
    (this + live).forEach { byBucket[bucket(it.at, bucket)] = it }
    return byBucket.values.sortedBy { it.at }
}

private fun bucket(
    at: Instant,
    size: Duration,
): Long = at.epochSeconds.floorDiv(size.inWholeSeconds)

/**
 * The monitor with its series extended by [history]: the provider's own points from earlier refreshes, and for
 * days it reports nothing, uptime estimated from the up/down samples of refreshes that day.
 */
fun Monitor.withHistory(history: MonitorHistory?): Monitor {
    if (history == null) return this
    val reported = history.dailyUptime.mergedWith(dailyUptime, 1.days)
    val reportedDays = reported.map { startOfUtcDay(it.at) }.toSet()
    val sampled =
        history.samples
            .filter { it.status == MonitorStatus.UP || it.status == MonitorStatus.DOWN }
            .groupBy { startOfUtcDay(it.at) }
            .filterKeys { it !in reportedDays }
            .map { (day, samples) ->
                val up = samples.count { it.status == MonitorStatus.UP }
                TimedValue(day, PERCENT * up / samples.size)
            }
    return copy(
        responseTimes = history.responseTimes.mergedWith(responseTimes, HistoryStore.RESPONSE_BUCKET),
        dailyUptime = (reported + sampled).sortedBy { it.at },
    )
}

private const val PERCENT = 100.0

/** Per-account history on disk; one file per account, like the snapshot cache, and deleted with the account. */
interface HistoryStore {
    fun load(): Map<String, AccountHistory>

    suspend fun save(history: AccountHistory)

    suspend fun keepOnly(accountIds: Collection<String>)

    object None : HistoryStore {
        override fun load(): Map<String, AccountHistory> = emptyMap()

        override suspend fun save(history: AccountHistory) = Unit

        override suspend fun keepOnly(accountIds: Collection<String>) = Unit
    }

    companion object {
        /** How far back history reaches; older points are compacted away on write. */
        val RETENTION: Duration = 90.days

        /** Response-time points closer together than this are the same reading. */
        val RESPONSE_BUCKET: Duration = 1.hours

        /** Refreshes closer together than this count as one up/down sample. */
        val SAMPLE_BUCKET: Duration = 15.minutes
    }
}

class FileHistoryStore(
    fileSystem: FileSystem,
    dir: Path,
    io: CoroutineDispatcher = Dispatchers.IO,
) : HistoryStore {
    private val files = JsonDirectory(fileSystem, dir, io)

    override fun load(): Map<String, AccountHistory> =
        files.readAll(AccountHistory.serializer()).values.associateBy { it.accountId }

    override suspend fun save(history: AccountHistory) =
        files.write(history.accountId, AccountHistory.serializer(), history)

    override suspend fun keepOnly(accountIds: Collection<String>) = files.keepOnly(accountIds)
}
