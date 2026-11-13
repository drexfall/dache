package dev.dache.app.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dache.core.dashboard.DashboardRepository
import dev.dache.core.dashboard.DashboardState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Shared by the overview and every account page; they are views of the same dashboard state. */
class DashboardViewModel(
    private val dashboard: DashboardRepository,
    clock: Clock = Clock.System,
) : ViewModel() {
    val state: StateFlow<DashboardState> = dashboard.state

    init {
        // What is loaded (the cache, or a widget's fetch) shows at once; a refresh runs behind it unless it's
        // fresh, so reopening the app a moment later doesn't repeat every request.
        if (dashboard.state.value.isStale(clock.now(), FRESH_FOR) || dashboard.state.value.lastUpdated == null) {
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch { dashboard.refresh() }
    }

    private companion object {
        val FRESH_FOR: Duration = 1.minutes
    }
}
