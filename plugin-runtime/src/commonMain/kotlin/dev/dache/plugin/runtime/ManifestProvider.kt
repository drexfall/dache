package dev.dache.plugin.runtime

import dev.dache.core.account.tokenKey
import dev.dache.core.model.AccountInfo
import dev.dache.core.model.AccountSetting
import dev.dache.core.model.Capability
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.Site
import dev.dache.core.provider.DevOpsProvider
import dev.dache.core.security.TokenVault
import dev.dache.plugin.api.FieldSpec
import dev.dache.plugin.api.PluginManifest
import dev.dache.plugin.api.ResourceSpec
import dev.dache.plugin.runtime.http.PluginHttp
import dev.dache.plugin.runtime.mapping.Binder
import dev.dache.plugin.runtime.mapping.Expression
import dev.dache.plugin.runtime.mapping.ManifestKind
import dev.dache.plugin.runtime.mapping.Mapping
import dev.dache.plugin.runtime.mapping.asText
import io.ktor.client.HttpClient
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
    private val lists = ListFetcher(http)
    private val validate = CompiledList(manifest.validate.request, items = null)
    private val displayName = Expression.parse(manifest.validate.displayName)
    private val resources = manifest.resources.map(::CompiledResource)

    private class CompiledResource(
        spec: ResourceSpec,
    ) {
        val kind = checkNotNull(ManifestKind.named(spec.kind)) { "manifest was not validated" }
        val list = CompiledList(spec.request, spec.items)
        val parents = spec.forEach?.let { CompiledList(it.request, it.items) }
        val mapping = Mapping(spec.fields, spec.statusMap)

        /** Nested fields with their own request, sent once per item. */
        val requested =
            spec.fields
                .mapNotNull { (name, field) ->
                    (field as? FieldSpec.Nested)?.request?.let { name to CompiledList(it, field.from) }
                }.toMap()
    }

    override val accountSettings: List<AccountSetting> =
        manifest.settings.map { AccountSetting(it.key, it.label, it.hint, it.required) }

    override suspend fun validateCredentials(
        token: String,
        settings: Map<String, String>,
    ): Result<AccountInfo> =
        attempt {
            val missing = manifest.settings.filter { it.required && settings[it.key].isNullOrBlank() }
            if (missing.isNotEmpty()) throw ProviderError.Unknown(MissingSettingException(missing.map { it.label }))
            val url = validate.url(null, settings) ?: throw ProviderError.Unknown(InvalidSettingException())
            val body = http.getJson(url, validate.headers, token)
            AccountInfo(displayName = displayName.evaluate(body)?.asText()?.ifBlank { null } ?: manifest.name)
        }

    override suspend fun fetchSnapshot(account: ConnectedAccount): Result<ProviderSnapshot> =
        attempt {
            val token = vault.get(account.tokenKey()) ?: throw ProviderError.Unauthorized()
            val context = AccountContext(token, account.settings)
            val sites = mutableListOf<Site>()
            resources.forEach { resource ->
                val records = records(resource, items(resource, context), context)
                when (resource.kind) {
                    ManifestKind.SITE -> sites += records.mapNotNull { Binder.site(it, id, account.id) }
                }
            }
            ProviderSnapshot(sites = sites)
        }

    private suspend fun items(
        resource: CompiledResource,
        account: AccountContext,
    ): List<JsonElement> {
        val parents = resource.parents?.let { lists.fetch(it, account) } ?: return lists.fetch(resource.list, account)
        return fanOut(parents) { parent -> lists.fetch(resource.list, account, parent) }.flatten()
    }

    /** Items past the fan-out limit keep their own fields but get no requested nested records. */
    private suspend fun records(
        resource: CompiledResource,
        items: List<JsonElement>,
        account: AccountContext,
    ): List<Mapping.Record> {
        val settings = account.settings
        if (resource.requested.isEmpty()) return items.map { resource.mapping.Record(it, settings) }
        val fetched =
            fanOut(items) { item ->
                resource.requested.mapValues { (_, list) -> lists.fetch(list, account, parent = item) }
            }
        val none = emptyMap<String, List<JsonElement>>()
        return items.mapIndexed { index, item ->
            resource.mapping.Record(item, settings, fetched.getOrElse(index) { none })
        }
    }

    private inline fun <T> attempt(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: ProviderError) {
            Result.failure(e)
        }
}

/** A required account setting was left blank. Names the settings by label, never by value. */
class MissingSettingException(
    labels: List<String>,
) : Exception("missing ${labels.joinToString()}")

/** A setting's value can't be used in a request, e.g. `..` in a path. */
class InvalidSettingException : Exception("a setting can't be used in a request")
