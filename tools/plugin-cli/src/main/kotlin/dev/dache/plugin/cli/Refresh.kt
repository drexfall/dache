package dev.dache.plugin.cli

import dev.dache.core.account.tokenKey
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderId
import dev.dache.core.security.TokenKey
import dev.dache.core.security.TokenVault
import dev.dache.plugin.api.PluginManifest
import dev.dache.plugin.runtime.LoadResult
import dev.dache.plugin.runtime.ManifestLoader
import dev.dache.plugin.runtime.ManifestProvider
import dev.dache.plugin.runtime.http.createPluginHttpClient
import io.ktor.client.engine.HttpClientEngine
import java.io.File

/**
 * Validates the manifest at [file], checks [token] and refreshes once through [engine], and describes what came
 * back. [settings] are the account settings the manifest asks for, by key.
 */
suspend fun refresh(
    file: File,
    engine: HttpClientEngine,
    token: String,
    settings: Map<String, String>,
): Outcome {
    val report = validate(file)
    val manifest = (ManifestLoader.load(file.readText()) as? LoadResult.Loaded)?.manifest
    if (manifest == null) return Outcome(report.toString(), ok = false)
    val id = providerId(manifest)
    val account = ConnectedAccount(id = "cli", providerId = id, displayName = manifest.name, settings = settings)
    val vault = SingleTokenVault(account.tokenKey(), token)
    val provider = ManifestProvider(manifest, id, vault, createPluginHttpClient(engine))
    val check = provider.checkToken(token, settings)
    val snapshot = check.mapCatching { provider.fetchSnapshot(account).getOrThrow() }
    val text =
        listOfNotNull(
            check.fold(::describe) { "token check failed: ${it.describe()}" },
            snapshot.getOrNull()?.let(::describe),
            snapshot.exceptionOrNull()?.takeIf { check.isSuccess }?.let { "refresh failed: ${it.describe()}" },
        ).joinToString("\n")
    return Outcome(text, ok = snapshot.isSuccess)
}

/** The provider the manifest's id names; any other manifest borrows the first, which only shows in fallbacks. */
private fun providerId(manifest: PluginManifest): ProviderId =
    ProviderId.entries.firstOrNull { it.id == manifest.id } ?: ProviderId.entries.first()

/** The error's type and cause, never a token: provider errors carry no response content. */
private fun Throwable.describe(): String =
    listOfNotNull(this::class.simpleName, cause?.let { "${it::class.simpleName}: ${it.message}" }).joinToString(" ")

private class SingleTokenVault(
    private val key: TokenKey,
    private val token: String,
) : TokenVault {
    override suspend fun put(
        key: TokenKey,
        token: String,
    ) = Unit

    override suspend fun get(key: TokenKey): String? = token.takeIf { key == this.key }

    override suspend fun delete(key: TokenKey) = Unit
}
