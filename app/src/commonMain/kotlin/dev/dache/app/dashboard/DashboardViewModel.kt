package dev.dache.app.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dache.core.dashboard.DashboardRepository
import dev.dache.core.dashboard.DashboardState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Shared by the overview and every account page; they are views of the same dashboard state. */
class DashboardViewModel(
    private val dashboard: DashboardRepository,
) : ViewModel() {
    val state: StateFlow<DashboardState> = dashboard.state

    init {
        // Show whatever is already loaded (e.g. by a widget) and only fetch on first open.
        if (dashboard.state.value.lastUpdated == null) refresh()
    }

    fun refresh() {
        viewModelScope.launch { dashboard.refresh() }
    }
}
