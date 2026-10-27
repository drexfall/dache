package dev.dache.core.provider

import dev.dache.core.model.AccountInfo
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

    suspend fun validateCredentials(token: String): Result<AccountInfo>

    suspend fun fetchSnapshot(account: ConnectedAccount): Result<ProviderSnapshot>
}
