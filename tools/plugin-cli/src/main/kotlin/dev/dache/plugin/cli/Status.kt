package dev.dache.plugin.cli

import dev.dache.core.model.ProviderStatus
import dev.dache.plugin.runtime.LoadResult
import dev.dache.plugin.runtime.ManifestLoader
import dev.dache.plugin.runtime.providerFor
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import java.io.File

/** Reads the manifest's public status page, which needs no token, and prints what the app would show. */
fun status(
    file: File,
    client: HttpClient,
): Outcome {
    val manifest =
        (
            ManifestLoader.load(
                file.takeIf { it.isFile }?.readText().orEmpty(),
            ) as? LoadResult.Loaded
        )?.manifest
    val provider = manifest?.let { providerFor(it, SingleTokenVault.none(), client) }
    return when {
        provider == null -> Outcome(validate(file).toString(), ok = false)
        !provider.hasStatus -> Outcome("${manifest.name} has no status section", ok = false)
        else ->
            runBlocking { provider.fetchStatus() }.fold(
                onSuccess = { Outcome(describe(it), ok = true) },
                onFailure = { Outcome("status failed: ${it::class.simpleName}", ok = false) },
            )
    }
}

fun describe(status: ProviderStatus): String =
    buildString {
        appendLine("${status.level}  ${status.description.orEmpty()}")
        val groups = status.components.groupBy { it.group }
        groups.forEach { (group, components) ->
            val impaired = components.filter { it.level.isImpaired }
            appendLine("  ${group ?: "(no group)"}: ${components.size} components, ${impaired.size} impaired")
            impaired.take(MAX_LISTED).forEach { appendLine("    ${it.level}  ${it.name}") }
        }
        status.incidents.forEach { appendLine("  incident: ${it.impact}  ${it.name} (${it.status})") }
    }.trimEnd()

private const val MAX_LISTED = 5
