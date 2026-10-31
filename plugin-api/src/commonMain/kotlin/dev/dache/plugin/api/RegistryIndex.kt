package dev.dache.plugin.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The plugin registry's `index.json`: every published plugin's latest version, signed as a whole with the
 * registry key (the signature is `index.json.sig`). [serial] grows with every publish, so a client can refuse an
 * older index replayed at it.
 */
@Serializable
data class RegistryIndex(
    val indexVersion: Int,
    val serial: Long,
    val plugins: List<IndexEntry>,
) {
    companion object {
        /** The index format this build reads and writes. */
        const val CURRENT_VERSION = 1

        /** Lenient, unlike manifests: a newer registry may add keys that older apps don't know yet. */
        private val json =
            Json {
                ignoreUnknownKeys = true
                classDiscriminator = "type"
                prettyPrint = true
                encodeDefaults = true
            }

        /** @throws SerializationException when [text] is not an index. */
        fun decode(text: String): RegistryIndex = json.decodeFromString(serializer(), text)

        fun encode(index: RegistryIndex): String = json.encodeToString(serializer(), index)
    }
}

/**
 * One plugin. [path] is the manifest's location relative to the index and [sha256] the lowercase hex digest of its
 * exact bytes. [hosts], [auth] and [capabilities] repeat the manifest's, so a client can tell what an update
 * changes before downloading it.
 */
@Serializable
data class IndexEntry(
    val id: String,
    val name: String,
    val version: String,
    val publisher: String? = null,
    val path: String,
    val sha256: String,
    val hosts: List<String>,
    val auth: AuthSpec,
    val capabilities: List<String>,
)
