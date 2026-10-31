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

            val viewModel = DashboardViewModel(graph.dashboard)

            assertNotNull(viewModel.state.value.lastUpdated)
            assertTrue(
                viewModel.state.value.accounts
                    .isEmpty(),
            )
        }

    @Test
    fun oneAccountPerProviderFillsTheDashboard() =
        runTest(dispatcher) {
            val graph = TestGraph(CoroutineScope(backgroundScope.coroutineContext + dispatcher))
            val viewModel = DashboardViewModel(graph.dashboard)

            viewModel.state.test {
                ProviderId.entries.forEach { graph.accounts.connect(it, "mock-token") }
                viewModel.refresh()
                val loaded = expectMostRecentItem()
                assertEquals(6, loaded.results.size)
                assertTrue(loaded.results.all { it.error == null })
                assertEquals(1, loaded.summary.failingDeploys)
                assertTrue(loaded.links.isNotEmpty())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(6, graph.vault.tokens.size)
        }
}
