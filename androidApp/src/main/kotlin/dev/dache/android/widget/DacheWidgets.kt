package dev.dache.android.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dev.dache.android.DacheApplication
import dev.dache.core.alerts.AlertSettings
import dev.dache.core.dashboard.DashboardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

object DacheWidgets {
    suspend fun updateAll(context: Context) {
        SummaryWidget().updateAll(context)
        DeploysWidget().updateAll(context)
        MonitorsWidget().updateAll(context)
    }
}

/**
 * Returns the shared repository. With nothing to show it fetches first; with a cached refresh it draws that and,
 * when the cache is older than the shortest check interval, refreshes behind it. The age check also stops the
 * redraw a refresh triggers from starting another refresh.
 */
internal suspend fun Context.loadedDashboard(): DashboardRepository {
    val container = (applicationContext as DacheApplication).container
    val dashboard = container.dashboardRepository
    val state = dashboard.state.value
    when {
        state.lastUpdated == null -> dashboard.refresh()
        state.isStale(Clock.System.now(), AlertSettings.MIN_INTERVAL_MINUTES.minutes) -> container.refreshInBackground()
    }
    return dashboard
}

/** Whether widgets leave names out: the app lock is on and its widget setting asks for it. */
internal fun Context.widgetDetailsHidden(): Flow<Boolean> =
    (applicationContext as DacheApplication)
        .container.settingsStore.appLock
        .map { it.widgetDetailsHidden }
        .distinctUntilChanged()
