package dev.dache.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.dache.android.R
import dev.dache.core.dashboard.DashboardState
import dev.dache.core.model.Monitor
import dev.dache.core.model.MonitorStatus
import kotlinx.coroutines.flow.first

/** Uptime monitors with up/down counts, down monitors first. */
class MonitorsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val dashboard = context.loadedDashboard()
        val detailsHidden = context.widgetDetailsHidden()
        // Read before the first frame, so a hidden widget never shows names even briefly.
        val hiddenAtStart = detailsHidden.first()
        provideContent {
            val state by dashboard.state.collectAsState()
            val hidden by detailsHidden.collectAsState(hiddenAtStart)
            GlanceTheme { MonitorsContent(state, hidden) }
        }
    }
}

class MonitorsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonitorsWidget()
}

@Composable
private fun MonitorsContent(
    state: DashboardState,
    detailsHidden: Boolean,
) {
    val context = LocalContext.current
    WidgetFrame {
        WidgetTitle(R.string.widget_title_monitors)
        val monitors = state.snapshots.flatMap { it.monitors }.sortedBy { it.status != MonitorStatus.DOWN }
        when {
            state.accounts.isEmpty() -> WidgetCaption(context.getString(R.string.widget_empty))
            monitors.isEmpty() -> WidgetCaption(context.getString(R.string.widget_none))
            else -> {
                val up = monitors.count { it.status == MonitorStatus.UP }
                val down = monitors.count { it.status == MonitorStatus.DOWN }
                WidgetCaption(context.getString(R.string.widget_monitor_counts, up, down))
                if (!detailsHidden) {
                    LazyColumn(GlanceModifier.defaultWeight()) {
                        items(monitors, itemId = { it.hashCode().toLong() }) { monitor -> MonitorLine(monitor) }
                    }
                }
            }
        }
        UpdatedCaption(state.lastUpdated)
    }
}

@Composable
private fun MonitorLine(monitor: Monitor) {
    val status = monitor.status.widgetStatus()
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(GlanceModifier.defaultWeight()) {
            Text(
                monitor.name,
                style =
                    TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.onSurface,
                    ),
                maxLines = 1,
            )
            Text(
                monitor.url,
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
                maxLines = 1,
            )
        }
        Text(
            LocalContext.current.getString(status.label),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = status.color),
        )
    }
}
