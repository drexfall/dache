package dev.dache.plugin.runtime

import dev.dache.core.account.tokenKey
import dev.dache.core.model.AccountInfo
import dev.dache.core.model.AccountSetting
import dev.dache.core.model.Capability
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.ServicePage
import dev.dache.core.model.TokenCheck
import dev.dache.core.provider.DevOpsProvider
import dev.dache.core.security.TokenVault
import dev.dache.plugin.api.PluginManifest
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

    override val pages: List<ServicePage> = manifestPages(manifest)

    private val http = PluginHttp(client, manifest, logger)
    private val lists = ListFetcher(http)
    private val validate = CompiledList(manifest.validate.request, items = null)
    private val displayName = Expression.parse(manifest.validate.displayName)
    private val resources = manifest.resources.map(::CompiledResource)
    private val checker = TokenChecker(lists, resources, manifest.validate.permission)

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

    override suspend fun checkToken(
        token: String,
        settings: Map<String, String>,
    ): Result<TokenCheck> =
        validateCredentials(token, settings).map { info ->
            TokenCheck(info.displayName, checker.check(AccountContext(token, settings)))
        }

    override suspend fun fetchSnapshot(account: ConnectedAccount): Result<ProviderSnapshot> =
        attempt {
            val token = vault.get(account.tokenKey()) ?: throw ProviderError.Unauthorized()
            val context = AccountContext(token, account.settings)
            val binder = Binder(id, account.id)
            resources.fold(ProviderSnapshot()) { snapshot, resource ->
                val fetched = readable(resource.optional) { records(resource, items(resource, context), context) }
                val missing = fetched?.missing ?: listOfNotNull(resource.permission)
                add(snapshot, resource, fetched?.records.orEmpty(), binder)
                    .let { it.copy(missingPermissions = it.missingPermissions + missing) }
            }
        }

    private fun add(
        snapshot: ProviderSnapshot,
        resource: CompiledResource,
        records: List<Mapping.Record>,
        binder: Binder,
    ): ProviderSnapshot =
        when (resource.kind) {
            ManifestKind.SITE -> snapshot.copy(sites = snapshot.sites + records.mapNotNull(binder::site))
            ManifestKind.DOMAIN -> snapshot.copy(domains = snapshot.domains + records.mapNotNull(binder::domain))
            ManifestKind.DNS_RECORD ->
                snapshot.copy(dnsRecords = snapshot.dnsRecords + records.mapNotNull(binder::dnsRecord))
            ManifestKind.CI_RUN -> snapshot.copy(ciRuns = snapshot.ciRuns + records.mapNotNull(binder::ciRun))
            ManifestKind.WORKER -> snapshot.copy(workers = snapshot.workers + records.mapNotNull(binder::worker))
            ManifestKind.SERVICE -> {
                val service = checkNotNull(resource.service) { "manifest was not validated" }
                snapshot.copy(services = snapshot.services + records.mapNotNull { binder.service(it, service) })
            }
        }

    private suspend fun items(
        resource: CompiledResource,
        account: AccountContext,
    ): List<JsonElement> {
        val parents = resource.parents?.let { lists.fetch(it, account) } ?: return lists.fetch(resource.list, account)
        return fanOut(parents) { parent -> lists.fetch(resource.list, account, parent) }.flatten()
    }

    /** The records, and the permissions of optional nested lists the token couldn't read. */
    private class Fetched(
        val records: List<Mapping.Record>,
        val missing: Set<String>,
    )

    /** Items past the fan-out limit keep their own fields but get no requested nested records. */
    private suspend fun records(
        resource: CompiledResource,
        items: List<JsonElement>,
        account: AccountContext,
    ): Fetched {
        val settings = account.settings
        if (resource.requested.isEmpty()) {
            return Fetched(
                items.map { resource.mapping.Record(it, settings) },
                emptySet(),
            )
        }
        val fetched =
            fanOut(items) { item ->
                resource.requested.mapValues { (_, field) ->
                    readable(field.optional) { lists.fetch(field.list, account, parent = item) }
                }
            }
        val missing =
            fetched
                .flatMap { byField -> byField.filterValues { it == null }.keys }
                .mapNotNull { resource.requested[it]?.permission }
                .toSet()
        val none = emptyMap<String, List<JsonElement>>()
        val records =
            items.mapIndexed { index, item ->
                val nested = fetched.getOrNull(index)?.mapValues { it.value.orEmpty() } ?: none
                resource.mapping.Record(item, settings, nested)
            }
        return Fetched(records, missing)
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
