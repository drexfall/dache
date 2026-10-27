package dev.dache.plugin.runtime

import dev.dache.core.account.tokenKey
import dev.dache.core.model.AccountInfo
import dev.dache.core.model.Capability
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.Site
import dev.dache.core.provider.DevOpsProvider
import dev.dache.core.security.TokenVault
import dev.dache.plugin.api.PagingSpec
import dev.dache.plugin.api.PluginManifest
import dev.dache.plugin.api.RequestSpec
import dev.dache.plugin.api.ResourceSpec
import dev.dache.plugin.runtime.http.InvalidResponseException
import dev.dache.plugin.runtime.http.PluginHttp
import dev.dache.plugin.runtime.mapping.Binder
import dev.dache.plugin.runtime.mapping.Expression
import dev.dache.plugin.runtime.mapping.ManifestKind
import dev.dache.plugin.runtime.mapping.Mapping
import dev.dache.plugin.runtime.mapping.Value
import dev.dache.plugin.runtime.mapping.asText
import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/**
 * Runs a declarative plugin. [manifest] must have passed [ManifestValidator] (use [ManifestLoader]); this
 * class trusts it to parse. The token is read from [vault] for each refresh and only handed to the transport.
 */
class ManifestProvider(
    private val manifest: PluginManifest,
    override val id: ProviderId,
    private val vault: TokenVault,
    client: HttpClient,
    logger: RequestLogger = RequestLogger.None,
) : DevOpsProvider {
    override val capabilities: Set<Capability> =
        manifest.capabilities.mapNotNull { name -> Capability.entries.firstOrNull { it.name == name } }.toSet()

    private val http = PluginHttp(client, manifest, logger)
    private val displayName = Expression.parse(manifest.validate.displayName)
    private val resources = manifest.resources.map(::CompiledResource)

    private class CompiledResource(
        spec: ResourceSpec,
    ) {
        val kind = checkNotNull(ManifestKind.named(spec.kind)) { "manifest was not validated" }
        val request = spec.request
        val items = spec.items?.let(Expression::parse)
        val mapping = Mapping(spec.fields, spec.statusMap)
    }

    override suspend fun validateCredentials(token: String): Result<AccountInfo> =
        attempt {
            val body = http.getJson(url(manifest.validate.request), manifest.validate.request.headers, token)
            AccountInfo(displayName = displayName.evaluate(body)?.asText()?.ifBlank { null } ?: manifest.name)
        }

    override suspend fun fetchSnapshot(account: ConnectedAccount): Result<ProviderSnapshot> =
        attempt {
            val token = vault.get(account.tokenKey()) ?: throw ProviderError.Unauthorized()
            val sites = mutableListOf<Site>()
            resources.forEach { resource ->
                val records = fetchAll(resource, token).map { resource.mapping.Record(it) }
                when (resource.kind) {
                    ManifestKind.SITE -> sites += records.mapNotNull { Binder.site(it, id, account.id) }
                }
            }
            ProviderSnapshot(sites = sites)
        }

    private suspend fun fetchAll(
        resource: CompiledResource,
        token: String,
    ): List<JsonElement> {
        val request = resource.request
        return when (val paging = request.paging) {
            null -> items(resource, http.getJson(url(request), request.headers, token))
            is PagingSpec.Page -> fetchPages(resource, paging, token)
        }
    }

    /** Stops at a short page (or an empty one when the page size isn't sent), or after `maxPages`. */
    private suspend fun fetchPages(
        resource: CompiledResource,
        paging: PagingSpec.Page,
        token: String,
    ): List<JsonElement> {
        val all = mutableListOf<JsonElement>()
        for (index in 0 until paging.maxPages) {
            val params = mapOf(paging.param to (paging.start + index).toString()) + pageSize(paging)
            val page = items(resource, http.getJson(url(resource.request, params), resource.request.headers, token))
            all += page
            val last = if (paging.sizeParam != null) page.size < paging.size else page.isEmpty()
            if (last) break
        }
        return all
    }

    private fun pageSize(paging: PagingSpec.Page): Map<String, String> =
        paging.sizeParam?.let { mapOf(it to paging.size.toString()) }.orEmpty()

    private fun items(
        resource: CompiledResource,
        body: JsonElement,
    ): List<JsonElement> {
        val list = resource.items?.evaluate(body) ?: (body as? JsonArray)?.let(Value::Items)
        return (list as? Value.Items)?.values
            ?: throw ProviderError.Unknown(InvalidResponseException("expected a list at ${resource.items ?: "$"}"))
    }

    private fun url(
        request: RequestSpec,
        extra: Map<String, String> = emptyMap(),
    ): Url =
        URLBuilder(request.url)
            .apply {
                request.query.forEach { (name, value) -> parameters.append(name, value) }
                extra.forEach { (name, value) -> parameters[name] = value }
            }.build()

    private inline fun <T> attempt(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: ProviderError) {
            Result.failure(e)
        }
}
