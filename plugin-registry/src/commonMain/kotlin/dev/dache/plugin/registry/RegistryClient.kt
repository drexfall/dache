package dev.dache.plugin.registry

import dev.dache.plugin.api.IndexEntry
import dev.dache.plugin.api.PluginManifest
import dev.dache.plugin.api.RegistryIndex
import dev.dache.plugin.runtime.LoadResult
import dev.dache.plugin.runtime.ManifestLoader
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlin.io.encoding.Base64

/** Why the registry or one of its plugins was refused. Each message is safe to show as is. */
class RegistryException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** A manifest fetched from the registry whose bytes matched the signed index. */
class VerifiedManifest(
    val manifest: PluginManifest,
    val json: String,
    val sha256: String,
)

/**
 * Reads the signed plugin index at [indexUrl] and the manifests it lists. The index must carry a valid signature
 * from one of [publicKeys] (raw Ed25519, base64), and every manifest must match the digest the index gives it,
 * so nothing reaches the app that the registry key didn't vouch for.
 */
class RegistryClient(
    private val client: HttpClient,
    private val indexUrl: String = RegistryKeys.INDEX_URL,
    private val publicKeys: List<String> = RegistryKeys.pinned,
) {
    private val base = indexUrl.substringBeforeLast('/') + "/"

    /** The index, refused when it isn't signed by a pinned key or its serial is below [minSerial]. */
    suspend fun index(minSerial: Long = 0): Result<RegistryIndex> =
        attempt {
            val bytes = fetch(indexUrl)
            val signature =
                runCatching { Base64.decode(fetch("$indexUrl.sig").decodeToString().trim()) }
                    .getOrElse { throw RegistryException("The registry's signature is unreadable.", it) }
            if (publicKeys.none { verifyEd25519(bytes, signature, Base64.decode(it)) }) {
                throw RegistryException("The registry index isn't signed by Dache's key.")
            }
            val index =
                try {
                    RegistryIndex.decode(bytes.decodeToString())
                } catch (e: SerializationException) {
                    throw RegistryException("The registry index is unreadable.", e)
                }
            when {
                index.indexVersion != RegistryIndex.CURRENT_VERSION ->
                    throw RegistryException("This registry needs a newer version of Dache.")
                index.serial < minSerial ->
                    throw RegistryException("The registry sent an older index than one already seen.")
            }
            index
        }

    /** [entry]'s manifest, checked against its digest and, once parsed, against what the index says it is. */
    suspend fun manifest(entry: IndexEntry): Result<VerifiedManifest> =
        attempt {
            if (entry.path.split('/').any { it == ".." || it.isEmpty() }) {
                throw RegistryException("${entry.name} has an invalid path.")
            }
            val bytes = fetch(base + entry.path)
            val digest = sha256Hex(bytes)
            if (digest != entry.sha256.lowercase()) {
                throw RegistryException("${entry.name} doesn't match the registry's checksum.")
            }
            val manifest = load(bytes.decodeToString(), entry.name)
            val matches =
                manifest.id == entry.id &&
                    manifest.version == entry.version &&
                    manifest.hosts == entry.hosts &&
                    manifest.auth == entry.auth
            if (!matches) throw RegistryException("${entry.name} differs from its registry entry.")
            VerifiedManifest(manifest, bytes.decodeToString(), digest)
        }

    /** A manifest from a link, for sideloading. HTTPS only, and no bigger than any real manifest. */
    suspend fun download(url: String): Result<String> =
        attempt {
            if (!url.trim().startsWith("https://")) throw RegistryException("Links must start with https://.")
            val bytes = fetch(url.trim())
            if (bytes.size > MAX_MANIFEST_BYTES) throw RegistryException("That file is too large to be a plugin.")
            bytes.decodeToString()
        }

    private suspend fun fetch(url: String): ByteArray {
        val response = client.get(url)
        if (!response.status.isSuccess()) {
            throw RegistryException("The plugin registry answered ${response.status.value}.")
        }
        return response.readRawBytes()
    }

    // Network failures surface as RegistryException so the screen has one kind of error to explain.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> attempt(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: RegistryException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(RegistryException("The plugin registry can't be reached.", e))
        }

    companion object {
        /** Bundled manifests are under 20 KB; a hundred times that is no plugin. */
        const val MAX_MANIFEST_BYTES = 2_000_000

        /** Parses and validates a manifest, naming it [label] in the error. */
        fun load(
            json: String,
            label: String,
        ): PluginManifest =
            when (val result = ManifestLoader.load(json)) {
                is LoadResult.Loaded -> result.manifest
                is LoadResult.Invalid ->
                    throw RegistryException("$label isn't a valid plugin: ${result.problems.first()}")
            }
    }
}

/** Where the registry lives and the keys its index must be signed with. See the registry's README on rotation. */
object RegistryKeys {
    const val INDEX_URL = "https://raw.githubusercontent.com/drexfall/dache-plugins/index/index.json"

    val pinned: List<String> = listOf("zz14nlO7yt4dUa1RzyGCvPin2UqD3U8G1J2AkwbhGwI=")
}
