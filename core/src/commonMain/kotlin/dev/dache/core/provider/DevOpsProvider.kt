package dev.dache.core.provider

import dev.dache.core.model.AccountInfo
import dev.dache.core.model.AccountSetting
import dev.dache.core.model.Capability
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ProviderSnapshot

/**
 * Common contract for every provider. Failures are returned as `Result.failure(ProviderError)`.
 * Implementations read the account's token from the TokenVault themselves.
 */
interface DevOpsProvider {
    val id: ProviderId
    val capabilities: Set<Capability>

    /** What the user fills in besides the token when connecting. */
    val accountSettings: List<AccountSetting> get() = emptyList()

    /** [settings] are the values for [accountSettings] the account will be connected with. */
    suspend fun validateCredentials(
        token: String,
        settings: Map<String, String> = emptyMap(),
    ): Result<AccountInfo>

    suspend fun fetchSnapshot(account: ConnectedAccount): Result<ProviderSnapshot>
}
