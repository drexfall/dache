package dev.dache.core.dashboard

import dev.dache.core.model.ProviderId
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.ProviderStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okio.FileSystem
import okio.Path
import kotlin.time.Instant

/** One account's last successful fetch, as kept on disk. Holds no token: the models never carry one. */
@Serializable
data class CachedSnapshot(
    val accountId: String,
    val fetchedAt: Instant,
    val snapshot: ProviderSnapshot,
)

/** What a cold start has before any request: the last snapshot of each account and the provider statuses. */
data class CachedDashboard(
    val snapshots: List<CachedSnapshot> = emptyList(),
    val statuses: List<ProviderStatus> = emptyList(),
)

/**
 * Keeps the last refresh so the app, its widgets and a background check start from data instead of a blank
 * screen. Replaced on every successful fetch; history over time is [HistoryStore]'s job.
 */
interface SnapshotStore {
    /** Everything cached. Read once at startup, so it blocks; the files are small. */
    fun load(): CachedDashboard

    suspend fun save(snapshot: CachedSnapshot)

    suspend fun saveStatuses(statuses: Collection<ProviderStatus>)

    /** Forgets every account except [accountIds]. */
    suspend fun keepOnly(accountIds: Collection<String>)

    /** No cache: every start is cold. For tests and tools. */
    object None : SnapshotStore {
        override fun load() = CachedDashboard()

        override suspend fun save(snapshot: CachedSnapshot) = Unit

        override suspend fun saveStatuses(statuses: Collection<ProviderStatus>) = Unit

        override suspend fun keepOnly(accountIds: Collection<String>) = Unit
    }
}

/**
 * One JSON file per account under [dir] (`<accountId>.json`), plus `statuses.json`. A file that can't be read,
 * because the models changed or the write was cut short, is dropped rather than failing the start.
 */
class FileSnapshotStore(
    fileSystem: FileSystem,
    dir: Path,
    io: CoroutineDispatcher = Dispatchers.IO,
) : SnapshotStore {
    private val files = JsonDirectory(fileSystem, dir, io)

    override fun load(): CachedDashboard {
        val snapshots =
            files
                .readAll(CachedSnapshot.serializer())
                .filterKeys { it != STATUSES_KEY }
                .values
                .toList()
        val statuses = files.read(STATUSES_KEY, ListSerializer(ProviderStatus.serializer())).orEmpty()
        return CachedDashboard(snapshots, statuses)
    }

    override suspend fun save(snapshot: CachedSnapshot) =
        files.write(snapshot.accountId, CachedSnapshot.serializer(), snapshot)

    override suspend fun saveStatuses(statuses: Collection<ProviderStatus>) =
        files.write(STATUSES_KEY, ListSerializer(ProviderStatus.serializer()), statuses.toList())

    override suspend fun keepOnly(accountIds: Collection<String>) = files.keepOnly(accountIds + STATUSES_KEY)

    private companion object {
        const val STATUSES_KEY = "statuses"
    }
}

/** The newest fetch among [snapshots], or null when the cache is empty. */
fun CachedDashboard.lastUpdated(): Instant? = snapshots.maxOfOrNull { it.fetchedAt }

/** The provider each cached status belongs to. */
fun CachedDashboard.statusesByProvider(): Map<ProviderId, ProviderStatus> = statuses.associateBy { it.providerId }
