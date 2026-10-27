package dev.dache.app

import app.cash.turbine.test
import dev.dache.app.dashboard.DashboardViewModel
import dev.dache.core.model.ProviderId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun firstOpenRefreshesEvenWithoutAccounts() =
        runTest(dispatcher) {
            val graph = TestGraph(CoroutineScope(backgroundScope.coroutineContext + dispatcher))

            val viewModel = DashboardViewModel(graph.dashboard, graph.accounts)

            assertNotNull(viewModel.state.value.lastUpdated)
            assertTrue(
                viewModel.state.value.accounts
                    .isEmpty(),
            )
        }

    @Test
    fun demoAccountsFillEveryProvider() =
        runTest(dispatcher) {
            val graph = TestGraph(CoroutineScope(backgroundScope.coroutineContext + dispatcher))
            val viewModel = DashboardViewModel(graph.dashboard, graph.accounts)

            viewModel.state.test {
                viewModel.loadDemoAccounts()
                val loaded = expectMostRecentItem()
                assertEquals(6, loaded.results.size)
                assertTrue(loaded.results.all { it.error == null })
                assertEquals(1, loaded.summary.failingDeploys)
                assertTrue(loaded.links.isNotEmpty())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(6, graph.vault.tokens.size)
        }

    @Test
    fun demoSkipsProvidersBackedByRealPlugins() =
        runTest(dispatcher) {
            val graph = TestGraph(CoroutineScope(backgroundScope.coroutineContext + dispatcher))
            val sample = ProviderId.entries.toSet() - ProviderId.NETLIFY
            val viewModel = DashboardViewModel(graph.dashboard, graph.accounts, sample)

            viewModel.state.test {
                viewModel.loadDemoAccounts()
                val loaded = expectMostRecentItem()
                assertEquals(sample, loaded.accounts.map { it.providerId }.toSet())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
