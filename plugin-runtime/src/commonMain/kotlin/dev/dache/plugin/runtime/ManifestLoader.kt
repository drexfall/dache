package dev.dache.plugin.runtime

import dev.dache.plugin.api.ManifestJson
import dev.dache.plugin.api.PluginManifest

sealed interface LoadResult {
    data class Loaded(
        val manifest: PluginManifest,
    ) : LoadResult

    data class Invalid(
        val problems: List<ManifestProblem>,
    ) : LoadResult
}

/** Decodes and validates manifest JSON. Only a [LoadResult.Loaded] manifest may be handed to the engine. */
object ManifestLoader {
    fun load(text: String): LoadResult {
        val manifest =
            try {
                ManifestJson.decode(text)
            } catch (e: IllegalArgumentException) {
                // Includes SerializationException, which extends it.
                return LoadResult.Invalid(listOf(ManifestProblem("$", e.message.orEmpty())))
            }
        val problems = ManifestValidator.validate(manifest)
        return if (problems.isEmpty()) LoadResult.Loaded(manifest) else LoadResult.Invalid(problems)
    }
}
