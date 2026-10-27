package dev.dache.app.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dache.core.account.AccountRepository
import dev.dache.core.dashboard.DashboardRepository
import dev.dache.core.dashboard.DashboardState
import dev.dache.core.model.ProviderId
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Shared by the overview and every account page; they are views of the same dashboard state.
 * [sampleDataProviders] are the providers the demo may connect: those that serve sample data.
 */
class DashboardViewModel(
    private val dashboard: DashboardRepository,
    private val accounts: AccountRepository,
    private val sampleDataProviders: Set<ProviderId> = ProviderId.entries.toSet(),
) : ViewModel() {
    val state: StateFlow<DashboardState> = dashboard.state

    init {
        // Show whatever is already loaded (e.g. by a widget) and only fetch on first open.
        if (dashboard.state.value.lastUpdated == null) refresh()
    }

    fun refresh() {
        viewModelScope.launch { dashboard.refresh() }
    }

    /**
     * Connects one fake account per sample-data provider so the dashboard can be tried without real tokens.
     * Providers backed by a real plugin are skipped: they would only reject the fake token.
     */
    fun loadDemoAccounts() {
        viewModelScope.launch {
            ProviderId.entries.filter { it in sampleDataProviders }.forEach { accounts.connect(it, DEMO_TOKEN) }
            dashboard.refresh()
        }
    }

    companion object {
        const val DEMO_TOKEN = "demo-token-not-a-secret"
    }
}
