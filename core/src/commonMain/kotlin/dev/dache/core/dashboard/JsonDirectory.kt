package dev.dache.core.dashboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.IOException
import okio.Path

/**
 * A directory of small JSON files, one per key: `<key>.json`. Reads are synchronous and forgiving, since they
 * happen once at startup and a file that can't be read is simply absent until the next write replaces it.
 * Writes go to a sibling file first and move into place, so a crash mid-write keeps the previous version.
 */
class JsonDirectory(
    private val fileSystem: FileSystem,
    private val dir: Path,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun <T> read(
        key: String,
        serializer: KSerializer<T>,
    ): T? = readFile(fileFor(key), serializer)

    /** Every file's contents, keyed by file name without the extension. */
    fun <T> readAll(serializer: KSerializer<T>): Map<String, T> {
        if (!fileSystem.exists(dir)) return emptyMap()
        return fileSystem
            .list(dir)
            .filter { it.name.endsWith(SUFFIX) }
            .mapNotNull { path -> readFile(path, serializer)?.let { path.name.removeSuffix(SUFFIX) to it } }
            .toMap()
    }

    suspend fun <T> write(
        key: String,
        serializer: KSerializer<T>,
        value: T,
    ) = withContext(Dispatchers.IO) {
        val path = fileFor(key)
        fileSystem.createDirectories(dir)
        val tmp = dir / "${path.name}.tmp"
        fileSystem.write(tmp) { writeUtf8(json.encodeToString(serializer, value)) }
        fileSystem.atomicMove(tmp, path)
    }

    /** Deletes every file whose key isn't in [keys]. */
    suspend fun keepOnly(keys: Collection<String>) =
        withContext(Dispatchers.IO) {
            if (!fileSystem.exists(dir)) return@withContext
            val keep = keys.map { fileFor(it) }.toSet()
            fileSystem
                .list(dir)
                .filter { it.name.endsWith(SUFFIX) && it !in keep }
                .forEach { path -> runCatching { fileSystem.delete(path) } }
        }

    // A file that can't be read is treated as absent.
    @Suppress("SwallowedException")
    private fun <T> readFile(
        path: Path,
        serializer: KSerializer<T>,
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

    private fun fileFor(key: String): Path = dir / "${safe(key)}$SUFFIX"

    companion object {
        private const val SUFFIX = ".json"

        /** Keys are account ids (UUIDs) or fixed names; anything else is kept out of file names. */
        fun safe(key: String): String = key.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
    }
}
