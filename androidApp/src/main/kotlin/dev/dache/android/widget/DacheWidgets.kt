package dev.dache.android.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dev.dache.android.DacheApplication
import dev.dache.core.dashboard.DashboardRepository

object DacheWidgets {
    suspend fun updateAll(context: Context) {
        SummaryWidget().updateAll(context)
        DeploysWidget().updateAll(context)
        MonitorsWidget().updateAll(context)
    }
}

/** Returns the shared repository, fetching once if nothing has been loaded in this process yet. */
internal suspend fun Context.loadedDashboard(): DashboardRepository {
    val dashboard = (applicationContext as DacheApplication).container.dashboardRepository
    if (dashboard.state.value.lastUpdated == null) dashboard.refresh()
    return dashboard
}
