package dev.dache.core.dashboard

import dev.dache.core.account.AccountRepository
import dev.dache.core.fakes.InMemoryAccountStore
import dev.dache.core.fakes.InMemoryTokenVault
import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.core.provider.mock.MockProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardRepositoryTest {
    private val now = Instant.parse("2026-10-27T12:00:00Z")
    private val clock =
        object : Clock {
            override fun now(): Instant = now
        }
    private val vault = InMemoryTokenVault()
    private val providers = ProviderId.entries.associateWith { MockProvider(it, vault, clock, Duration.ZERO) }

    private fun TestScope.subject(): Pair<AccountRepository, DashboardRepository> {
        val accounts = AccountRepository(InMemoryAccountStore(), vault, providers)
        // Unconfined so the state collector keeps up; advanceUntilIdle does not drive backgroundScope work.
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        return accounts to DashboardRepository(accounts, providers, scope, clock)
    }

    @Test
    fun oneFailingAccountKeepsTheOthers() =
        runTest {
            val (accounts, dashboard) = subject()
            accounts.connect(ProviderId.NETLIFY, "fake-token-123").getOrThrow()
            val failing = accounts.connect(ProviderId.GITHUB, "${MockProvider.FAIL_PREFIX}token").getOrThrow()

            dashboard.refresh()

            val state = dashboard.state.value
            assertEquals(2, state.results.size)
            val failed = state.results.single { it.account == failing }
            assertIs<ProviderError.RateLimited>(failed.error)
            assertNull(failed.snapshot)
            assertEquals(
                2,
                state.results
                    .single { it.account != failing }
                    .snapshot
                    ?.sites
                    ?.size,
            )
            assertEquals(now, state.lastUpdated)
            assertFalse(state.isRefreshing)
        }

    @Test
    fun removingAnAccountDropsItsData() =
        runTest {
            val (accounts, dashboard) = subject()
            val netlify = accounts.connect(ProviderId.NETLIFY, "fake-token-123").getOrThrow()
            accounts.connect(ProviderId.UPTIMEROBOT, "fake-token-456").getOrThrow()
            dashboard.refresh()

            accounts.remove(netlify)

            val state = dashboard.state.value
            assertEquals(listOf(ProviderId.UPTIMEROBOT), state.results.map { it.account.providerId })
            assertTrue(state.snapshots.all { it.sites.isEmpty() })
            assertEquals(1, state.summary.downMonitors)
        }

    @Test
    fun linksAndSummaryAreComputedFromAllAccounts() =
        runTest {
            val (accounts, dashboard) = subject()
            ProviderId.entries.forEach { accounts.connect(it, "fake-token-123").getOrThrow() }

            dashboard.refresh()

            val state = dashboard.state.value
            assertNotNull(state.lastUpdated)
            assertTrue(state.links.isNotEmpty())
            assertEquals(1, state.summary.failingDeploys)
        }
}
