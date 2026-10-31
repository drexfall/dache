package dev.dache.plugin.runtime

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
import dev.dache.core.model.ProviderStatus
import dev.dache.core.model.ServicePage
import dev.dache.core.model.TokenCheck
import dev.dache.core.model.TokenPermission
import dev.dache.core.provider.DevOpsProvider
import dev.dache.core.security.TokenVault
import dev.dache.plugin.api.ManifestJson
import dev.dache.plugin.api.PluginManifest
import dev.dache.plugin.api.SelfHostedSpec
import dev.dache.plugin.runtime.http.createPluginHttpClient
import dev.dache.plugin.runtime.http.normalizeFingerprint
import dev.dache.plugin.runtime.http.pinnedHttpEngine
import dev.dache.plugin.runtime.http.probeCertificate
import dev.dache.plugin.runtime.security.HostGuard
import io.ktor.client.HttpClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs a self-hosted manifest. Each account names its server; requests for that account go to that server
 * only, through a [ManifestProvider] built from the manifest with the placeholder host replaced. A server with a
 * certificate the system trusts is used as is. One with a self-signed certificate fails with
 * [ProviderError.UntrustedCertificate] until the user trusts that exact certificate, which is then pinned.
 */
class SelfHostedProvider(
    private val template: PluginManifest,
    private val vault: TokenVault,
    private val client: HttpClient,
    private val logger: RequestLogger = RequestLogger.None,
) : DevOpsProvider {
    private val spec = checkNotNull(template.selfHosted) { "${template.id} isn't self-hosted" }
    private val reference = ManifestProvider(template, vault = vault, client = client, logger = logger)
    private val lock = Mutex()
    private val servers = mutableMapOf<Pair<Server, String?>, ManifestProvider>()

    override val id: ProviderId = reference.id
    override val capabilities: Set<Capability> = reference.capabilities
    override val pages: List<ServicePage> = reference.pages
    override val tokenPermissions: List<TokenPermission> get() = reference.tokenPermissions
    override val actions: List<ActionInfo> get() = reference.actions
    override val hasStatus: Boolean get() = reference.hasStatus

    override suspend fun fetchStatus(): Result<ProviderStatus> = reference.fetchStatus()

    override val accountSettings: List<AccountSetting> =
        listOf(
            AccountSetting(SelfHostedSpec.HOST_SETTING, spec.label, spec.hint, required = true),
            AccountSetting(
                SelfHostedSpec.CERTIFICATE_SETTING,
                "Trusted certificate (SHA-256)",
                "Only for a self-signed certificate; filled in when you choose to trust it.",
            ),
        ) + reference.accountSettings

    override suspend fun validateCredentials(
        token: String,
        settings: Map<String, String>,
    ): Result<AccountInfo> = forServer(settings) { it.validateCredentials(token, settings) }

    override suspend fun checkToken(
        token: String,
        settings: Map<String, String>,
    ): Result<TokenCheck> = forServer(settings) { it.checkToken(token, settings) }

    override suspend fun fetchSnapshot(account: ConnectedAccount): Result<ProviderSnapshot> =
        forServer(account.settings) { it.fetchSnapshot(account) }

    override suspend fun actionDefaults(
        account: ConnectedAccount,
        request: ActionRequest,
    ): Result<Map<String, String>> = forServer(account.settings) { it.actionDefaults(account, request) }

    override suspend fun previewAction(
        account: ConnectedAccount,
        request: ActionRequest,
    ): Result<ActionPreview> = forServer(account.settings) { it.previewAction(account, request) }

    override suspend fun runAction(
        account: ConnectedAccount,
        request: ActionRequest,
    ): Result<Unit> = forServer(account.settings) { it.runAction(account, request) }

    /**
     * Runs [block] against the account's server. A network failure on a server without a trusted certificate is
     * checked for a self-signed one, so the user can be offered to trust it.
     */
    private suspend fun <T> forServer(
        settings: Map<String, String>,
        block: suspend (ManifestProvider) -> Result<T>,
    ): Result<T> {
        val server =
            Server.parse(settings[SelfHostedSpec.HOST_SETTING])
                ?: return Result.failure(ProviderError.Unknown(InvalidSettingException()))
        val pin = settings[SelfHostedSpec.CERTIFICATE_SETTING]?.takeIf { it.isNotBlank() }?.let(::normalizeFingerprint)
        val result = block(providerFor(server, pin))
        val failure = result.exceptionOrNull()
        return if (failure is ProviderError.Network && pin == null) {
            val probe = probeCertificate(server.host, server.port)
            if (probe != null &&
                !probe.trusted
            ) {
                Result.failure(ProviderError.UntrustedCertificate(probe.fingerprint))
            } else {
                result
            }
        } else {
            result
        }
    }

    private suspend fun providerFor(
        server: Server,
        pin: String?,
    ): ManifestProvider =
        lock.withLock {
            servers.getOrPut(server to pin) {
                val manifest = forHost(server)
                val http = pin?.let { createPluginHttpClient(pinnedHttpEngine(it)) } ?: client
                ManifestProvider(manifest, id, vault, http, logger, port = server.port)
            }
        }

    /** The manifest with the placeholder host swapped for [server] in every URL. */
    private fun forHost(server: Server): PluginManifest {
        val text =
            ManifestJson
                .encode(template)
                .replace("https://${SelfHostedSpec.PLACEHOLDER_HOST}", "https://${server.authority}")
        return ManifestJson.decode(text).copy(hosts = listOf(server.host))
    }

    /** A server as the user typed it: `host`, `host:port`, optionally with `https://` and a trailing slash. */
    data class Server(
        val host: String,
        val port: Int,
    ) {
        val authority: String get() = if (port == HTTPS_PORT) host else "$host:$port"

        companion object {
            private const val HTTPS_PORT = 443
            private const val MAX_PORT = 65535

            fun parse(text: String?): Server? {
                val bare =
                    text
                        ?.trim()
                        ?.lowercase()
                        ?.removePrefix("https://")
                        ?.trimEnd('/') ?: return null
                val host = bare.substringBefore(':')
                val port = if (':' in bare) bare.substringAfter(':').toIntOrNull() else HTTPS_PORT
                val valid = port != null && port in 1..MAX_PORT && HostGuard.isValidServerHost(host) && '/' !in bare
                return if (valid) Server(host, port!!) else null
            }
        }
    }
}

/** The provider for [manifest]: self-hosted ones get a server per account, the rest run as they are. */
fun providerFor(
    manifest: PluginManifest,
    vault: TokenVault,
    client: HttpClient,
    logger: RequestLogger = RequestLogger.None,
): DevOpsProvider =
    if (manifest.selfHosted != null) {
        SelfHostedProvider(manifest, vault, client, logger)
    } else {
        ManifestProvider(manifest, vault = vault, client = client, logger = logger)
    }
