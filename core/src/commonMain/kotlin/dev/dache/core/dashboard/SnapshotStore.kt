package dev.dache.core.dashboard

import dev.dache.core.model.ProviderId
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.ProviderStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.IOException
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
    private val fileSystem: FileSystem,
    private val dir: Path,
) : SnapshotStore {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    private val statusesFile = dir / STATUSES_FILE

    override fun load(): CachedDashboard {
        if (!fileSystem.exists(dir)) return CachedDashboard()
        val snapshots =
            fileSystem
                .list(dir)
                .filter { it.name.endsWith(".json") && it != statusesFile }
                .mapNotNull { read(it, CachedSnapshot.serializer()) }
                .filter { it.accountId == accountIdOf(it.accountId) }
        val statuses = read(statusesFile, ListSerializer(ProviderStatus.serializer())).orEmpty()
        return CachedDashboard(snapshots, statuses)
    }

    override suspend fun save(snapshot: CachedSnapshot) =
        write(fileFor(snapshot.accountId), json.encodeToString(CachedSnapshot.serializer(), snapshot))

    override suspend fun saveStatuses(statuses: Collection<ProviderStatus>) =
        write(statusesFile, json.encodeToString(ListSerializer(ProviderStatus.serializer()), statuses.toList()))

    override suspend fun keepOnly(accountIds: Collection<String>) =
        withContext(Dispatchers.IO) {
            if (!fileSystem.exists(dir)) return@withContext
            val keep = accountIds.map { fileFor(it) }.toSet()
            fileSystem
                .list(dir)
                .filter { it != statusesFile && it !in keep && it.name.endsWith(".json") }
                .forEach { path -> runCatching { fileSystem.delete(path) } }
        }

    private fun fileFor(accountId: String): Path = dir / "${accountIdOf(accountId)}.json"

    // A file that can't be read is treated as absent: the next refresh rewrites it.
    @Suppress("SwallowedException")
    private fun <T> read(
        path: Path,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): T? =
        try {
            if (!fileSystem.exists(path)) return null
            json.decodeFromString(serializer, fileSystem.read(path) { readUtf8() })
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: IOException) {
            null
        }

    // Written whole to a sibling first, so a crash mid-write leaves the previous file, not half of the new one.
    private suspend fun write(
        path: Path,
        text: String,
    ) = withContext(Dispatchers.IO) {
        fileSystem.createDirectories(dir)
        val tmp = dir / "${path.name}.tmp"
        fileSystem.write(tmp) { writeUtf8(text) }
        fileSystem.atomicMove(tmp, path)
    }

    private companion object {
        const val STATUSES_FILE = "statuses.json"

        /** Account ids are UUIDs; anything else is kept out of file names. */
        fun accountIdOf(accountId: String): String = accountId.filter { it.isLetterOrDigit() || it == '-' }
    }
}

/** The newest fetch among [snapshots], or null when the cache is empty. */
fun CachedDashboard.lastUpdated(): Instant? = snapshots.maxOfOrNull { it.fetchedAt }

/** The provider each cached status belongs to. */
fun CachedDashboard.statusesByProvider(): Map<ProviderId, ProviderStatus> = statuses.associateBy { it.providerId }
