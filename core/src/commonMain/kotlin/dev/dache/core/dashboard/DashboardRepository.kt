package dev.dache.core.dashboard

import dev.dache.core.account.AccountRepository
import dev.dache.core.linking.ResourceLinker
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.Resource
import dev.dache.core.model.ResourceLink
import dev.dache.core.model.ResourceRef
import dev.dache.core.model.ServicePage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Instant

/** Result of fetching one account. Exactly one of [snapshot] or [error] is set. */
data class AccountResult(
    val account: ConnectedAccount,
    val snapshot: ProviderSnapshot?,
    val error: ProviderError?,
)

data class DashboardState(
    val accounts: List<ConnectedAccount> = emptyList(),
    val results: List<AccountResult> = emptyList(),
    val links: List<ResourceLink> = emptyList(),
    val summary: DashboardSummary = DashboardSummary(0, 0, 0, 0),
    val lastUpdated: Instant? = null,
    val isRefreshing: Boolean = false,
    /** Each provider's pages beside an account's dashboard. */
    val pages: Map<ProviderId, List<ServicePage>> = emptyMap(),
) {
    val snapshots: List<ProviderSnapshot> get() = results.mapNotNull { it.snapshot }

    /** Looks a resource up by reference across every loaded account. */
    fun resource(ref: ResourceRef): Resource? =
        snapshots.asSequence().flatMap { it.resources }.firstOrNull {
            it.ref ==
                ref
        }

    fun pages(providerId: ProviderId): List<ServicePage> = pages[providerId].orEmpty()

    fun page(
        providerId: ProviderId,
        pageId: String,
    ): ServicePage? = pages(providerId).firstOrNull { it.id == pageId }

    /** Resources linked to [ref], on any provider. */
    fun linkedTo(ref: ResourceRef): List<ResourceRef> = links.mapNotNull { it.other(ref) }
}

/**
 * Fetches every connected account in parallel. One account failing only marks that account as errored;
 * the rest of the dashboard still updates. Data for removed accounts is dropped immediately.
 */
class DashboardRepository(
    private val accountRepository: AccountRepository,
    scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) {
    private val catalog = accountRepository.catalog
    private val fetched = MutableStateFlow(DashboardState())
    private val refreshLock = Mutex()

    val state: StateFlow<DashboardState> =
        combine(fetched, accountRepository.accounts, catalog.providers) { state, accounts, providers ->
            val ids = accounts.map { it.id }.toSet()
            val results = state.results.filter { it.account.id in ids }
            val pages = providers.mapValues { (_, provider) -> provider.pages }
            if (results.size == state.results.size) {
                state.copy(accounts = accounts, pages = pages)
            } else {
                derive(
                    results,
                    state.lastUpdated,
                ).copy(accounts = accounts, isRefreshing = state.isRefreshing, pages = pages)
            }
        }.stateIn(scope, SharingStarted.Eagerly, DashboardState())

    suspend fun refresh() {
        catalog.awaitReady()
        // Overlapping refreshes (pull-to-refresh plus a widget update) would just repeat the same calls.
        if (refreshLock.isLocked) return
        refreshLock.withLock {
            fetched.update { it.copy(isRefreshing = true) }
            val accounts = accountRepository.accounts.first()
            val results =
                coroutineScope {
                    accounts.map { account -> async { fetch(account) } }.awaitAll()
                }
            fetched.value = derive(results, clock.now())
        }
    }

    /** Refreshes, or waits for the refresh already under way, and returns what was fetched. */
    suspend fun refreshAndGet(): DashboardState {
        refresh()
        refreshLock.withLock { }
        return fetched.value
    }

    // A bug in one provider must never take down the whole overview, so anything it throws is contained here.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun fetch(account: ConnectedAccount): AccountResult {
        val provider =
            catalog[account.providerId]
                ?: return AccountResult(account, null, ProviderError.NotFound())
        val result =
            try {
                provider.fetchSnapshot(account)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(ProviderError.Unknown(e))
            }
        return result.fold(
            onSuccess = { AccountResult(account, it, null) },
            onFailure = { AccountResult(account, null, it as? ProviderError ?: ProviderError.Unknown(it)) },
        )
    }

    private fun derive(
        results: List<AccountResult>,
        lastUpdated: Instant?,
    ): DashboardState {
        val snapshots = results.mapNotNull { it.snapshot }
        return DashboardState(
            results = results,
            links = ResourceLinker.link(snapshots),
            summary = DashboardSummary.from(snapshots, clock.now()),
            lastUpdated = lastUpdated,
        )
    }
}
