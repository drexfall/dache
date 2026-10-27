package dev.dache.plugin.api

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object ManifestJson {
    /** The manifest schema version this build understands. */
    const val CURRENT_VERSION = 1

    /** Strict: unknown keys are errors, so a typo in a manifest fails loudly instead of being ignored. */
    val json =
        Json {
            ignoreUnknownKeys = false
            classDiscriminator = "type"
            prettyPrint = true
            encodeDefaults = false
        }

    /** @throws SerializationException when [text] is not a structurally valid manifest. */
    fun decode(text: String): PluginManifest = json.decodeFromString(PluginManifest.serializer(), text)

    fun encode(manifest: PluginManifest): String = json.encodeToString(PluginManifest.serializer(), manifest)
}
