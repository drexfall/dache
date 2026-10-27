package dev.dache.app

import app.cash.turbine.test
import dev.dache.app.accounts.AccountsViewModel
import dev.dache.core.model.ProviderError
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun rejectedTokenKeepsDialogOpenWithError() =
        runTest(dispatcher) {
            val graph = TestGraph(CoroutineScope(backgroundScope.coroutineContext + dispatcher))
            val viewModel = AccountsViewModel(graph.accounts, graph.dashboard)

            viewModel.state.test {
                viewModel.startAdding()
                viewModel.updateToken("short")
                viewModel.submit()

                val dialog = assertNotNull(expectMostRecentItem().addAccount)
                assertIs<ProviderError.Unauthorized>(dialog.error)
                assertFalse(dialog.isValidating)
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(graph.vault.tokens.isEmpty())
        }

    @Test
    fun acceptedTokenClosesDialogAndListsAccount() =
        runTest(dispatcher) {
            val graph = TestGraph(CoroutineScope(backgroundScope.coroutineContext + dispatcher))
            val viewModel = AccountsViewModel(graph.accounts, graph.dashboard)

            viewModel.state.test {
                viewModel.startAdding()
                viewModel.selectProvider(ProviderId.CLOUDFLARE)
                viewModel.updateToken("fake-token-123")
                viewModel.submit()

                val state = expectMostRecentItem()
                assertNull(state.addAccount)
                assertEquals(listOf(ProviderId.CLOUDFLARE), state.accounts.map { it.providerId })
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, graph.dashboard.state.value.results.size)
        }

    @Test
    fun connectingAnnouncesTheNewAccount() =
        runTest(dispatcher) {
            val graph = TestGraph(CoroutineScope(backgroundScope.coroutineContext + dispatcher))
            val viewModel = AccountsViewModel(graph.accounts, graph.dashboard)

            viewModel.connected.test {
                viewModel.startAdding()
                viewModel.selectProvider(ProviderId.UPTIMEROBOT)
                viewModel.updateToken("fake-token-123")
                viewModel.submit()

                assertEquals(ProviderId.UPTIMEROBOT, awaitItem().providerId)
            }
        }

    @Test
    fun removeDeletesAccountAndToken() =
        runTest(dispatcher) {
            val graph = TestGraph(CoroutineScope(backgroundScope.coroutineContext + dispatcher))
            val viewModel = AccountsViewModel(graph.accounts, graph.dashboard)
            val account = graph.accounts.connect(ProviderId.GITHUB, "fake-token-123").getOrThrow()

            viewModel.state.test {
                viewModel.remove(account)
                assertTrue(expectMostRecentItem().accounts.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(graph.vault.tokens.isEmpty())
        }
}
