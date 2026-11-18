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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
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
 * Fetches the active profile's accounts in parallel and shows them. One account failing only marks that account as
 * errored and keeps its last snapshot; the rest of the dashboard still updates. Other profiles keep what they last
 * fetched, so switching to one shows it at once while the switch refreshes it. Data for removed accounts is dropped
 * immediately. Starts from whatever [store] kept from the last refresh, so there is something to show before any
 * request.
 */
class DashboardRepository(
    private val accountRepository: AccountRepository,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val store: SnapshotStore = SnapshotStore.None,
    private val historyStore: HistoryStore = HistoryStore.None,
) {
    private val catalog = accountRepository.catalog
    private val cached = store.load()
    private val fetched = MutableStateFlow(restore(cached, historyStore.load()))

    /** When each account was last refreshed, failed or not; a profile's last update is its newest. */
    private val refreshedAt = MutableStateFlow(cached.snapshots.associate { it.accountId to it.fetchedAt })
    private val refreshLock = Mutex()

    /** Counts finished refreshes; [refreshedIn] records the one that last covered each account. Guarded by the lock. */
    @Volatile private var generation = 0L
    private val refreshedIn = mutableMapOf<String, Long>()

    init {
        scope.launch {
            accountRepository.accounts.collect { accounts ->
                val ids = accounts.map { it.id }
                store.keepOnly(ids)
                historyStore.keepOnly(ids)
            }
        }
        // Switching to a profile refreshes it, unless it was refreshed a moment ago.
        scope.launch {
            accountRepository.profiles
                .map { it.active.id }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    launch {
                        val ids = accountRepository.activeAccounts.first().map { it.id }
                        val last = ids.mapNotNull { refreshedAt.value[it] }.maxOrNull()
                        if (ids.isNotEmpty() && (last == null || clock.now() - last > SWITCH_FRESH_FOR)) refresh()
                    }
                }
        }
    }

    val state: StateFlow<DashboardState> =
        combine(fetched, accountRepository.activeAccounts, catalog.providers, refreshedAt) { state, accounts, _, at ->
            view(state, accounts, at)
        }.stateIn(scope, SharingStarted.Eagerly, DashboardState())

    /** [state] narrowed to [accounts], the active profile's, with that profile's own last update. */
    private fun view(
        state: DashboardState,
        accounts: List<ConnectedAccount>,
        refreshedAt: Map<String, Instant>,
    ): DashboardState {
        val ids = accounts.map { it.id }.toSet()
        val results = state.results.filter { it.account.id in ids }.map(::resolveProvider)
        val pages = catalog.providers.value.mapValues { (_, provider) -> provider.pages }
        val inUse = accounts.map { it.providerId }.toSet()
        val statuses =
            state.statuses.values
                .map(::resolveProvider)
                .filter { it.providerId in inUse }
                .associateBy { it.providerId }
        // A profile without accounts has nothing of its own to date; the last refresh stands in.
        val lastUpdated =
            if (accounts.isEmpty()) state.lastUpdated else accounts.mapNotNull { refreshedAt[it.id] }.maxOrNull()
        return derive(results, lastUpdated).copy(
            accounts = accounts,
            isRefreshing = state.isRefreshing,
            pages = pages,
            statuses = statuses,
            accountsLoaded = true,
            histories = state.histories.filterKeys { it in ids },
        )
    }

    /** Refreshes the active profile's accounts. Waits for a refresh under way, and skips if that one covered them. */
    suspend fun refresh() {
        catalog.awaitReady()
        val asked = generation
        refreshLock.withLock {
            // Read now, not when asked: after fast profile switching, the profile the user landed on is what counts.
            val accounts = accountRepository.activeAccounts.first()
            // Overlapping refreshes (pull-to-refresh plus a widget update) would just repeat the same calls.
            if (generation > asked && accounts.all { (refreshedIn[it.id] ?: -1L) > asked }) return
            fetched.update { it.copy(isRefreshing = true) }
            val everyone =
                accountRepository.accounts
                    .first()
                    .map { it.id }
                    .toSet()
            val previous = fetched.value
            val now = clock.now()
            val (results, statuses) =
                coroutineScope {
                    val results = accounts.map { account -> async { fetch(account, now, previous) } }
                    val statuses = accounts.map { it.providerId }.distinct().map { async { status(it) } }
                    results.awaitAll() to statuses.awaitAll().filterNotNull().toMap()
                }
            val refreshed = accounts.map { it.id }.toSet()
            // Other profiles' accounts keep their last results until they are shown and refreshed again.
            val kept = previous.results.filter { it.account.id !in refreshed && it.account.id in everyone }
            val merged = previous.statuses + statuses
            val fresh = results.filter { it.error == null && it.snapshot != null }
            val histories =
                previous.histories.filterKeys { it in everyone } +
                    fresh.associate { result ->
                        val id = result.account.id
                        id to (previous.histories[id] ?: AccountHistory(id)).absorb(result.snapshot!!, now)
                    }
            generation++
            refreshed.forEach { refreshedIn[it] = generation }
            refreshedAt.update { at -> at.filterKeys { it in everyone } + refreshed.associateWith { now } }
            fetched.value = derive(results + kept, now).copy(statuses = merged, histories = histories)
            fresh.forEach { result ->
                store.save(CachedSnapshot(result.account.id, now, result.snapshot!!))
                historyStore.save(histories.getValue(result.account.id))
            }
            store.saveStatuses(merged.values)
        }
    }

    /** Refreshes the active profile, or waits for the refresh already under way, and returns what it shows. */
    suspend fun refreshAndGet(): DashboardState {
        refresh()
        return view(fetched.value, accountRepository.activeAccounts.first(), refreshedAt.value)
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

    private companion object {
        /** How recent a profile's last refresh can be for switching to it not to refresh again. */
        val SWITCH_FRESH_FOR: Duration = 1.minutes
    }
}
