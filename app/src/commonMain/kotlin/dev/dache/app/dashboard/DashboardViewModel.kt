package dev.dache.app.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dache.core.account.AccountRepository
import dev.dache.core.dashboard.DashboardRepository
import dev.dache.core.dashboard.DashboardState
import dev.dache.core.model.ProviderId
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Shared by the overview and every account page; they are views of the same dashboard state. */
class DashboardViewModel(
    private val dashboard: DashboardRepository,
    private val accounts: AccountRepository,
) : ViewModel() {
    val state: StateFlow<DashboardState> = dashboard.state

    init {
        // Show whatever is already loaded (e.g. by a widget) and only fetch on first open.
        if (dashboard.state.value.lastUpdated == null) refresh()
    }

    fun refresh() {
        viewModelScope.launch { dashboard.refresh() }
    }

    /** Connects one fake account per provider so the dashboard can be tried without real tokens. */
    fun loadDemoAccounts() {
        viewModelScope.launch {
            ProviderId.entries.forEach { accounts.connect(it, DEMO_TOKEN) }
            dashboard.refresh()
        }
    }

    companion object {
        const val DEMO_TOKEN = "demo-token-not-a-secret"
    }
}
