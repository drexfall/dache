package dev.dache.core.provider.mock

import dev.dache.core.account.tokenKey
import dev.dache.core.model.AccountInfo
import dev.dache.core.model.Capability
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.provider.DevOpsProvider
import dev.dache.core.security.TokenVault
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Stand-in for a real provider until the manifest engine lands. Serves [MockFixtures] data.
 *
 * Token conventions for trying error states: shorter than [MIN_TOKEN_LENGTH] fails validation,
 * and a token starting with [FAIL_PREFIX] makes every refresh fail with a rate-limit error.
 */
class MockProvider(
    override val id: ProviderId,
    private val vault: TokenVault,
    private val clock: Clock = Clock.System,
    private val latency: Duration = 400.milliseconds,
) : DevOpsProvider {
    override val capabilities: Set<Capability> = MockFixtures.capabilities(id)

    override suspend fun validateCredentials(
        token: String,
        settings: Map<String, String>,
    ): Result<AccountInfo> {
        delay(latency)
        if (token.length < MIN_TOKEN_LENGTH) return Result.failure(ProviderError.Unauthorized())
        return Result.success(AccountInfo(displayName = "acme · ${id.displayName}"))
    }

    override suspend fun fetchSnapshot(account: ConnectedAccount): Result<ProviderSnapshot> {
        delay(latency)
        val token = vault.get(account.tokenKey())
        return when {
            token == null -> Result.failure(ProviderError.Unauthorized())
            token.startsWith(FAIL_PREFIX) -> Result.failure(ProviderError.RateLimited(retryAfter = 60.seconds))
            else -> Result.success(MockFixtures.snapshot(id, account.id, clock.now()))
        }
    }

    companion object {
        const val MIN_TOKEN_LENGTH = 8
        const val FAIL_PREFIX = "fail-"
    }
}
