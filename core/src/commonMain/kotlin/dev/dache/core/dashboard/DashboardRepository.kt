package dev.dache.core.dashboard

import dev.dache.core.account.AccountRepository
import dev.dache.core.linking.ResourceLinker
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.ProviderStatus
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Result of fetching one account. [snapshot] is the last successful fetch, taken at [fetchedAt]; [error] is set
 * when the latest refresh failed, in which case the snapshot is older than the refresh and shown as such.
 */
data class AccountResult(
    val account: ConnectedAccount,
    val snapshot: ProviderSnapshot?,
    val error: ProviderError?,
    val fetchedAt: Instant? = null,
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
    /** The public status of each provider in use that has a status page; the last one read survives a failure. */
    val statuses: Map<ProviderId, ProviderStatus> = emptyMap(),
    /** False until the stored accounts have been read; until then an empty [accounts] means nothing. */
    val accountsLoaded: Boolean = false,
    /** What each account's refreshes have shown over time, by account id; charts reach back through it. */
    val histories: Map<String, AccountHistory> = emptyMap(),
) {
    fun history(accountId: String): AccountHistory? = histories[accountId]

    val snapshots: List<ProviderSnapshot> get() = results.mapNotNull { it.snapshot }

    /** Whether what is shown is older than [threshold], the point past which pages say so plainly. */
    fun isStale(
        now: Instant,
        threshold: Duration,
    ): Boolean = lastUpdated?.let { now - it > threshold } ?: false

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
 * Fetches every connected account in parallel. One account failing only marks that account as errored and keeps
 * its last snapshot; the rest of the dashboard still updates. Data for removed accounts is dropped immediately.
 * Starts from whatever [store] kept from the last refresh, so there is something to show before any request.
 */
class DashboardRepository(
    private val accountRepository: AccountRepository,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val store: SnapshotStore = SnapshotStore.None,
    private val historyStore: HistoryStore = HistoryStore.None,
) {
    private val catalog = accountRepository.catalog
    private val fetched = MutableStateFlow(restore(store.load(), historyStore.load()))
    private val refreshLock = Mutex()

    init {
        scope.launch {
            accountRepository.accounts.collect { accounts ->
                val ids = accounts.map { it.id }
                store.keepOnly(ids)
                historyStore.keepOnly(ids)
            }
        }
    }

    val state: StateFlow<DashboardState> =
        combine(fetched, accountRepository.accounts, catalog.providers) { state, accounts, providers ->
            val ids = accounts.map { it.id }.toSet()
            val results = state.results.filter { it.account.id in ids }.map(::resolveProvider)
            val pages = providers.mapValues { (_, provider) -> provider.pages }
            val statuses =
                state.statuses.values
                    .map(::resolveProvider)
                    .associateBy { it.providerId }
            val histories = state.histories.filterKeys { it in ids }
            if (results.size == state.results.size) {
                state.copy(
                    accounts = accounts,
                    results = results,
                    pages = pages,
                    statuses = statuses,
                    accountsLoaded = true,
                    histories = histories,
                )
            } else {
                derive(results, state.lastUpdated).copy(
                    accounts = accounts,
                    isRefreshing = state.isRefreshing,
                    pages = pages,
                    statuses = statuses,
                    accountsLoaded = true,
                    histories = histories,
                )
            }
        }.stateIn(scope, SharingStarted.Eagerly, DashboardState())

    suspend fun refresh() {
        catalog.awaitReady()
        // Overlapping refreshes (pull-to-refresh plus a widget update) would just repeat the same calls.
        if (refreshLock.isLocked) return
        refreshLock.withLock {
            fetched.update { it.copy(isRefreshing = true) }
            val accounts = accountRepository.accounts.first()
            val previous = fetched.value
            val now = clock.now()
            val (results, statuses) =
                coroutineScope {
                    val results = accounts.map { account -> async { fetch(account, now, previous) } }
                    val statuses = accounts.map { it.providerId }.distinct().map { async { status(it) } }
                    results.awaitAll() to statuses.awaitAll().filterNotNull().toMap()
                }
            val merged = previous.statuses + statuses
            val fresh = results.filter { it.error == null && it.snapshot != null }
            val histories =
                previous.histories.filterKeys { id -> accounts.any { it.id == id } } +
                    fresh.associate { result ->
                        val id = result.account.id
                        id to (previous.histories[id] ?: AccountHistory(id)).absorb(result.snapshot!!, now)
                    }
            fetched.value = derive(results, now).copy(statuses = merged, histories = histories)
            fresh.forEach { result ->
                store.save(CachedSnapshot(result.account.id, now, result.snapshot!!))
                historyStore.save(histories.getValue(result.account.id))
            }
            store.saveStatuses(merged.values)
        }
    }

    /** Refreshes, or waits for the refresh already under way, and returns what was fetched. */
    suspend fun refreshAndGet(): DashboardState {
        refresh()
        refreshLock.withLock { }
        return fetched.value
    }

    /** The provider's public status, or null when it has none or it couldn't be read this time. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun status(id: ProviderId): Pair<ProviderId, ProviderStatus>? {
        val provider = catalog[id]?.takeIf { it.hasStatus } ?: return null
        return try {
            provider.fetchStatus().getOrNull()?.let { id to it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    // A bug in one provider must never take down the whole overview, so anything it throws is contained here.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun fetch(
        account: ConnectedAccount,
        now: Instant,
        previous: DashboardState,
    ): AccountResult {
        val last = previous.results.firstOrNull { it.account.id == account.id }

        fun failed(error: ProviderError) = AccountResult(account, last?.snapshot, error, last?.fetchedAt)
        val provider = catalog[account.providerId] ?: return failed(ProviderError.NotFound())
        val result =
            try {
                provider.fetchSnapshot(account)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(ProviderError.Unknown(e))
            }
        return result.fold(
            onSuccess = { AccountResult(account, it, null, now) },
            onFailure = { failed(it as? ProviderError ?: ProviderError.Unknown(it)) },
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

    /** The state a cold start begins with: the cache as results, dated by its newest fetch, plus the history. */
    private fun restore(
        cached: CachedDashboard,
        histories: Map<String, AccountHistory>,
    ): DashboardState {
        val results =
            cached.snapshots.map { entry ->
                // The account's name and settings come back with the account list; only its id matters here.
                val account = ConnectedAccount(entry.accountId, providerOf(entry.snapshot), "")
                AccountResult(account, entry.snapshot, null, entry.fetchedAt)
            }
        return derive(results, cached.lastUpdated()).copy(statuses = cached.statusesByProvider(), histories = histories)
    }

    private fun providerOf(snapshot: ProviderSnapshot): ProviderId =
        snapshot.resources.firstOrNull()?.providerId ?: ProviderId("")

    /**
     * A result read from the cache knows its provider by id alone; once the plugin is installed its resources get
     * the plugin's name and links. Same-instance ids are already resolved, so live results cost nothing here.
     */
    private fun resolveProvider(result: AccountResult): AccountResult {
        val provider = catalog.resolve(result.account.providerId)
        if (provider === result.account.providerId) return result
        return result.copy(
            account = result.account.copy(providerId = provider),
            snapshot = result.snapshot?.withProvider(provider),
        )
    }

    private fun resolveProvider(status: ProviderStatus): ProviderStatus {
        val provider = catalog.resolve(status.providerId)
        return if (provider === status.providerId) status else status.copy(providerId = provider)
    }
}
