package dev.dache.core.icons

import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderIcon
import dev.dache.core.model.ProviderId
import dev.dache.core.provider.DevOpsProvider
import dev.dache.core.provider.ProviderCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * The icon to show for each provider and account. A plugin's own icon comes first; without one, its site's favicon
 * once fetched; the UI draws a letter when there's neither.
 */
data class ProviderIcons(
    val byProvider: Map<ProviderId, ProviderIcon> = emptyMap(),
    val byAccount: Map<String, ProviderIcon> = emptyMap(),
) {
    /** [provider]'s icon, as seen on the account [accountId] when given: self-hosted accounts differ by server. */
    fun of(
        provider: ProviderId,
        accountId: String? = null,
    ): ProviderIcon? = provider.icon ?: accountId?.let(byAccount::get) ?: byProvider[provider]
}

/**
 * Fetches and caches favicons for providers whose plugin ships no icon: one per provider, plus one per account
 * whose site differs (a self-hosted server). Each site is fetched once and then again only after [refetchAfter]
 * (or sooner if it had none, or couldn't be reached), never on every launch.
 */
class ProviderIconRepository(
    private val catalog: ProviderCatalog,
    accounts: Flow<List<ConnectedAccount>>,
    private val scope: CoroutineScope,
    private val store: IconStore = IconStore.None,
    private val clock: Clock = Clock.System,
) {
    private val cache = MutableStateFlow(store.load().associateBy { it.site })

    private val targets: Flow<List<Target>> = combine(catalog.providers, accounts, ::targetsFor)

    val icons: StateFlow<ProviderIcons> =
        combine(targets, cache) { targets, cache ->
            val found = targets.mapNotNull { target -> cache[target.site]?.icon?.let { target to it } }
            ProviderIcons(
                byProvider =
                    found
                        .filter { (t, _) ->
                            t.accountId == null
                        }.associate { (t, icon) -> t.provider to icon },
                byAccount = found.mapNotNull { (t, icon) -> t.accountId?.let { it to icon } }.toMap(),
            )
        }.stateIn(scope, SharingStarted.Eagerly, ProviderIcons())

    /** Keeps icons current as plugins and accounts come and go. Call once. */
    fun start(): Job =
        scope.launch {
            catalog.awaitReady()
            targets.collectLatest { targets ->
                store.keepOnly(targets.map { it.site }.toSet())
                cache.update { it.filterKeys { site -> targets.any { t -> t.site == site } } }
                fetchDue(targets)
            }
        }

    private suspend fun fetchDue(targets: List<Target>) =
        coroutineScope {
            targets
                .distinctBy { it.site }
                .filter { isDue(cache.value[it.site]) }
                .map { target -> async { fetch(target) } }
                .awaitAll()
        }

    private suspend fun fetch(target: Target) {
        val result = target.source.fetchIcon(target.settings)
        val now = clock.now()
        val entry =
            result.fold(
                onSuccess = { CachedIcon.of(target.site, now, it) },
                onFailure = { CachedIcon(target.site, now, data = cache.value[target.site]?.data, failed = true) },
            )
        cache.update { it + (target.site to entry) }
        store.save(entry)
    }

    private fun isDue(entry: CachedIcon?): Boolean {
        entry ?: return true
        val wait: Duration =
            when {
                entry.failed -> retryAfterFailure
                entry.data == null -> retryWhenMissing
                else -> refetchAfter
            }
        return clock.now() - entry.fetchedAt >= wait
    }

    private fun targetsFor(
        providers: Map<ProviderId, DevOpsProvider>,
        accounts: List<ConnectedAccount>,
    ): List<Target> {
        val wanted = providers.values.filter { it.id.icon == null }
        val perProvider =
            wanted.mapNotNull { provider ->
                provider.iconSite(emptyMap())?.let { Target(it, provider, provider.id, accountId = null, emptyMap()) }
            }
        val perAccount =
            accounts.mapNotNull { account ->
                val provider = providers[account.providerId]?.takeIf { it.id.icon == null } ?: return@mapNotNull null
                provider.iconSite(account.settings)?.let {
                    Target(it, provider, provider.id, account.id, account.settings)
                }
            }
        return perProvider + perAccount
    }

    private class Target(
        val site: String,
        val source: DevOpsProvider,
        val provider: ProviderId,
        val accountId: String?,
        val settings: Map<String, String>,
    )

    companion object {
        val refetchAfter: Duration = 30.days
        val retryWhenMissing: Duration = 7.days
        val retryAfterFailure: Duration = 1.hours
    }
}
