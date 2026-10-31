package dev.dache.core.provider

import dev.dache.core.model.AccountInfo
import dev.dache.core.model.AccountSetting
import dev.dache.core.model.Capability
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.ServicePage
import dev.dache.core.model.TokenCheck
import dev.dache.core.model.TokenPermission

/**
 * Common contract for every provider. Failures are returned as `Result.failure(ProviderError)`.
 * Implementations read the account's token from the TokenVault themselves.
 */
interface DevOpsProvider {
    val id: ProviderId
    val capabilities: Set<Capability>

    /** What the user fills in besides the token when connecting. */
    val accountSettings: List<AccountSetting> get() = emptyList()

    /** How an account's resources are split into pages beside its dashboard. */
    val pages: List<ServicePage> get() = ServicePage.perKind(capabilities)

    /** [settings] are the values for [accountSettings] the account will be connected with. */
    suspend fun validateCredentials(
        token: String,
        settings: Map<String, String> = emptyMap(),
    ): Result<AccountInfo>

    /**
     * The permissions [checkToken] reports on, in the same order, so they can be listed before the user has a
     * token and while it is being checked. Providers that can't tell permissions apart have none.
     */
    val tokenPermissions: List<TokenPermission> get() = emptyList()

    /**
     * Validates [token] like [validateCredentials], then checks each of [tokenPermissions]. A token the provider
     * refuses outright comes back as a [TokenCheck] that isn't accepted rather than as a failure, where the
     * provider can say which permission the refusing request needed.
     */
    suspend fun checkToken(
        token: String,
        settings: Map<String, String> = emptyMap(),
    ): Result<TokenCheck> = validateCredentials(token, settings).map { TokenCheck(it.displayName, emptyList()) }

    suspend fun fetchSnapshot(account: ConnectedAccount): Result<ProviderSnapshot>
}
