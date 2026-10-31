package dev.dache.plugin.runtime

import dev.dache.core.account.tokenKey
import dev.dache.core.model.AccountInfo
import dev.dache.core.model.AccountSetting
import dev.dache.core.model.ActionInfo
import dev.dache.core.model.ActionPreview
import dev.dache.core.model.ActionRequest
import dev.dache.core.model.Capability
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.ServicePage
import dev.dache.core.model.TokenCheck
import dev.dache.core.model.TokenPermission
import dev.dache.core.provider.DevOpsProvider
import dev.dache.core.security.TokenVault
import dev.dache.plugin.api.PluginManifest
import dev.dache.plugin.runtime.http.PluginHttp
import dev.dache.plugin.runtime.http.RequestBudget
import dev.dache.plugin.runtime.mapping.Binder
import dev.dache.plugin.runtime.mapping.Expression
import dev.dache.plugin.runtime.mapping.ManifestKind
import dev.dache.plugin.runtime.mapping.Mapping
import dev.dache.plugin.runtime.mapping.asText
import io.ktor.client.HttpClient
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlin.time.TimeSource

/**
 * Runs a declarative plugin. [manifest] must have passed [ManifestValidator] (use [ManifestLoader]); this
 * class trusts it to parse. The token is read from [vault] for each refresh and only handed to the transport.
 */
class ManifestProvider(
    private val manifest: PluginManifest,
    override val id: ProviderId = manifest.providerId(),
    private val vault: TokenVault,
    client: HttpClient,
    logger: RequestLogger = RequestLogger.None,
    timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
    /** A self-hosted server's port; every other plugin talks to port 443. */
    port: Int = DEFAULT_PORT,
) : DevOpsProvider {
    override val capabilities: Set<Capability> =
        manifest.capabilities.mapNotNull { name -> Capability.entries.firstOrNull { it.name == name } }.toSet()

    override val pages: List<ServicePage> = manifestPages(manifest)

    private val http = PluginHttp(client, manifest, logger, timeSource, port)
    private val lists = ListFetcher(http)
    private val validate = CompiledList(manifest.validate.request, items = null)
    private val displayName = Expression.parse(manifest.validate.displayName)
    private val resources = manifest.resources.map(::CompiledResource)
    private val checker = TokenChecker(lists, resources, manifest.validate.permission)
    private val executor = ActionExecutor(manifest, http)

    override val actions: List<ActionInfo> get() = executor.infos

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

    override val tokenPermissions: List<TokenPermission> get() = checker.declared

    // A 403 on the request every usable token passes means the provider won't take this token as it is. Some
    // providers, like Cloudflare, answer an invalid token that way too, so it's reported as a refusal with the
    // permission that request needed, not as a bare error.
    override suspend fun checkToken(
        token: String,
        settings: Map<String, String>,
    ): Result<TokenCheck> {
        val validated = validateCredentials(token, settings)
        return when (val error = validated.exceptionOrNull()) {
            null -> validated.map { TokenCheck(it.displayName, checker.check(AccountContext(token, settings))) }
            is ProviderError.MissingScope -> Result.success(TokenCheck(displayName = null, checker.refused()))
            else -> Result.failure(error)
        }
    }

    override suspend fun fetchSnapshot(account: ConnectedAccount): Result<ProviderSnapshot> =
        attempt {
            withContext(RequestBudget(manifest.limits.maxRequestsPerRefresh)) { fetch(account) }
        }

    private suspend fun fetch(account: ConnectedAccount): ProviderSnapshot {
        val context = AccountContext(vault.tokenFor(account), account.settings)
        val binder = Binder(id, account.id)
        val targets = mutableMapOf<String, JsonElement>()
        val snapshot =
            resources.fold(ProviderSnapshot()) { snapshot, resource ->
                val fetched =
                    readable(resource.optional) { records(resource, items(resource, context), context) }
                        .getOrElse { Fetched.unread(it, resource.permission) }
                targets += ActionExecutor.index(resource.kind, fetched.records, resource.service?.id)
                add(snapshot, resource, fetched.records, binder).let {
                    it.copy(
                        missingPermissions = it.missingPermissions + fetched.missing,
                        unavailable = it.unavailable + fetched.unavailable,
                    )
                }
            }
        executor.remember(account.id, targets)
        return snapshot
    }

    override suspend fun actionDefaults(
        account: ConnectedAccount,
        request: ActionRequest,
    ): Result<Map<String, String>> = attempt { executor.defaults(account, request) }

    override suspend fun previewAction(
        account: ConnectedAccount,
        request: ActionRequest,
    ): Result<ActionPreview> = attempt { executor.preview(account, request, vault.tokenFor(account)) }

    override suspend fun runAction(
        account: ConnectedAccount,
        request: ActionRequest,
    ): Result<Unit> = attempt { executor.run(account, request, vault.tokenFor(account)) }

    private suspend fun items(
        resource: CompiledResource,
        account: AccountContext,
    ): List<JsonElement> {
        val parents = resource.parents?.let { lists.fetch(it, account) } ?: return lists.fetch(resource.list, account)
        return fanOut(parents) { parent -> lists.fetch(resource.list, account, parent) }.flatten()
    }

    /**
     * The records, and the permissions of optional lists that weren't read: [missing] for the ones the token was
     * refused, [unavailable] for the ones that failed some other way.
     */
    private class Fetched(
        val records: List<Mapping.Record>,
        val missing: Set<String> = emptySet(),
        val unavailable: Set<String> = emptySet(),
    ) {
        companion object {
            fun unread(
                error: Throwable,
                permission: String?,
            ): Fetched {
                val names = setOfNotNull(permission)
                return if (error.isRefusal) {
                    Fetched(
                        emptyList(),
                        missing = names,
                    )
                } else {
                    Fetched(emptyList(), unavailable = names)
                }
            }
        }
    }

    /** Items past the fan-out limit keep their own fields but get no requested nested records. */
    private suspend fun records(
        resource: CompiledResource,
        items: List<JsonElement>,
        account: AccountContext,
    ): Fetched {
        val settings = account.settings
        if (resource.requested.isEmpty()) return Fetched(items.map { resource.mapping.Record(it, settings) })
        val fetched =
            fanOut(items) { item ->
                resource.requested.mapValues { (_, field) ->
                    readable(field.optional) { lists.fetch(field.list, account, parent = item) }
                }
            }
        val errors =
            fetched.flatMap { byField ->
                byField.mapNotNull { (name, read) -> read.exceptionOrNull()?.let { name to it } }
            }

        fun permissions(refused: Boolean) =
            errors
                .filter { (_, error) ->
                    error.isRefusal == refused
                }.mapNotNull { (name, _) ->
                    resource.requested[name]?.permission
                }.toSet()
        val none = emptyMap<String, List<JsonElement>>()
        val records =
            items.mapIndexed { index, item ->
                val nested = fetched.getOrNull(index)?.mapValues { it.value.getOrDefault(emptyList()) } ?: none
                resource.mapping.Record(item, settings, nested)
            }
        return Fetched(records, missing = permissions(refused = true), unavailable = permissions(refused = false))
    }

    /** Provider errors, and action inputs that can't be used, become failures the UI can show. */
    private inline fun <T> attempt(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: ProviderError) {
            Result.failure(e)
        } catch (e: ActionInputException) {
            Result.failure(e)
        }
}

/** A required account setting was left blank. Names the settings by label, never by value. */
class MissingSettingException(
    labels: List<String>,
) : Exception("missing ${labels.joinToString()}")

/** A setting's value can't be used in a request, e.g. `..` in a path. */
class InvalidSettingException : Exception("a setting can't be used in a request")

/** The provider [this] manifest talks to, with its name and links. */
fun PluginManifest.providerId(): ProviderId =
    ProviderId(id = id, displayName = name, dashboardUrl = dashboardUrl, tokenPageUrl = tokenUrl, tokenHelp = tokenHelp)

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
        ManifestKind.MONITOR -> snapshot.copy(monitors = snapshot.monitors + records.mapNotNull(binder::monitor))
        ManifestKind.SERVER -> snapshot.copy(servers = snapshot.servers + records.mapNotNull(binder::server))
        ManifestKind.BILLING ->
            snapshot.copy(
                billing =
                    records.firstNotNullOfOrNull(binder::billing) ?: snapshot.billing,
            )
        ManifestKind.WORKER -> snapshot.copy(workers = snapshot.workers + records.mapNotNull(binder::worker))
        ManifestKind.SERVICE -> {
            val service = checkNotNull(resource.service) { "manifest was not validated" }
            snapshot.copy(services = snapshot.services + records.mapNotNull { binder.service(it, service) })
        }
    }

private suspend fun TokenVault.tokenFor(account: ConnectedAccount): String =
    get(account.tokenKey()) ?: throw ProviderError.Unauthorized()

private const val DEFAULT_PORT = 443
