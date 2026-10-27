package dev.dache.core.account

import dev.dache.core.fakes.InMemoryAccountStore
import dev.dache.core.fakes.InMemoryTokenVault
import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.core.provider.mock.MockProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class AccountRepositoryTest {
    private val store = InMemoryAccountStore()
    private val vault = InMemoryTokenVault()
    private val repository =
        AccountRepository(
            store = store,
            vault = vault,
            providers = mapOf(ProviderId.NETLIFY to MockProvider(ProviderId.NETLIFY, vault, latency = Duration.ZERO)),
            newId = { "acc-1" },
        )

    @Test
    fun connectStoresTrimmedTokenInVaultAndAccountWithoutToken() =
        runTest {
            val account = repository.connect(ProviderId.NETLIFY, "  fake-token-123  ").getOrThrow()

            assertEquals("fake-token-123", vault.tokens[account.tokenKey()])
            assertEquals(listOf(account), store.accounts.first())
            assertEquals("netlify", account.tokenKey().pluginId)
        }

    @Test
    fun invalidTokenIsNotStored() =
        runTest {
            val result = repository.connect(ProviderId.NETLIFY, "short")

            assertIs<ProviderError.Unauthorized>(result.exceptionOrNull())
            assertTrue(vault.tokens.isEmpty())
            assertTrue(store.accounts.first().isEmpty())
        }

    @Test
    fun unsupportedProviderFails() =
        runTest {
            val result = repository.connect(ProviderId.GITHUB, "fake-token-123")

            assertIs<ProviderError.NotFound>(result.exceptionOrNull())
        }

    @Test
    fun removeDeletesTokenAndAccount() =
        runTest {
            val account = repository.connect(ProviderId.NETLIFY, "fake-token-123").getOrThrow()

            repository.remove(account)

            assertTrue(vault.tokens.isEmpty())
            assertTrue(store.accounts.first().isEmpty())
        }
}
