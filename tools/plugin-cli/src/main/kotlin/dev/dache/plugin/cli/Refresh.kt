package dev.dache.plugin.cli

import dev.dache.core.account.tokenKey
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.security.TokenKey
import dev.dache.core.security.TokenVault
import dev.dache.plugin.api.SelfHostedSpec
import dev.dache.plugin.runtime.LoadResult
import dev.dache.plugin.runtime.ManifestLoader
import dev.dache.plugin.runtime.ManifestProvider
import dev.dache.plugin.runtime.providerFor
import dev.dache.plugin.runtime.providerId
import io.ktor.client.HttpClient
import java.io.File

/**
 * Validates the manifest at [file], checks [token] and refreshes once through [client], and describes what came
 * back. [settings] are the account settings the manifest asks for, by key. Make [client] with
 * createPluginHttpClient, so requests get the same timeouts and redirect handling as in the app.
 *
 * A self-hosted manifest given a `server` setting runs the way the app runs it, with every request going to that
 * server; without one, its requests keep the placeholder host, so recorded responses can sit under `self.hosted`.
 */
suspend fun refresh(
    file: File,
    client: HttpClient,
    token: String,
    settings: Map<String, String>,
): Outcome {
    val report = validate(file)
    val manifest = (ManifestLoader.load(file.readText()) as? LoadResult.Loaded)?.manifest
    if (manifest == null) return Outcome(report.toString(), ok = false)
    val id = manifest.providerId()
    val account = ConnectedAccount(id = "cli", providerId = id, displayName = manifest.name, settings = settings)
    val vault = SingleTokenVault(account.tokenKey(), token)
    val provider =
        if (manifest.selfHosted != null && SelfHostedSpec.HOST_SETTING !in settings) {
            ManifestProvider(manifest, id, vault, client)
        } else {
            providerFor(manifest, vault, client)
        }
    val check = provider.checkToken(token, settings)
    // A refused token would only fail again, so there is no refresh to show.
    val snapshot = check.mapCatching { if (it.accepted) provider.fetchSnapshot(account).getOrThrow() else null }
    val text =
        listOfNotNull(
            check.fold(::describe) { "token check failed: ${it.describe()}" },
            snapshot.getOrNull()?.let(::describe),
            snapshot.exceptionOrNull()?.takeIf { check.isSuccess }?.let { "refresh failed: ${it.describe()}" },
        ).joinToString("\n")
    return Outcome(text, ok = snapshot.getOrNull() != null)
}

/** The error's type and cause, never a token: provider errors carry no response content. */
private fun Throwable.describe(): String =
    listOfNotNull(this::class.simpleName, cause?.let { "${it::class.simpleName}: ${it.message}" }).joinToString(" ")

internal class SingleTokenVault(
    private val key: TokenKey,
    private val token: String,
) : TokenVault {
    override suspend fun put(
        key: TokenKey,
        token: String,
    ) = Unit

    override suspend fun get(key: TokenKey): String? = token.takeIf { key == this.key }

    override suspend fun delete(key: TokenKey) = Unit

    companion object {
        /** Holds nothing, for reads that need no token. */
        fun none() = SingleTokenVault(TokenKey("", ""), "")
    }
}
