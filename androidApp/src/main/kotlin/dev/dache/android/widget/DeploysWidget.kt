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
import dev.dache.core.model.DeployStatus
import dev.dache.core.model.Site

/** Latest deploy per site, failures first. */
class DeploysWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val dashboard = context.loadedDashboard()
        provideContent {
            val state by dashboard.state.collectAsState()
            GlanceTheme { DeploysContent(state) }
        }
    }
}

class DeploysWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DeploysWidget()
}

@Composable
private fun DeploysContent(state: DashboardState) {
    val context = LocalContext.current
    WidgetFrame {
        WidgetTitle(R.string.widget_title_deploys)
        val sites =
            state.snapshots
                .flatMap { it.sites }
                .sortedBy { if (it.latestDeployment?.status == DeployStatus.FAILED) 0 else 1 }
        when {
            state.accounts.isEmpty() -> WidgetCaption(context.getString(R.string.widget_empty))
            sites.isEmpty() -> WidgetCaption(context.getString(R.string.widget_none))
            else ->
                LazyColumn(GlanceModifier.defaultWeight()) {
                    items(sites, itemId = { it.hashCode().toLong() }) { site -> SiteLine(site) }
                }
        }
        UpdatedCaption(state.lastUpdated)
    }
}

@Composable
private fun SiteLine(site: Site) {
    val status = site.latestDeployment?.status.widgetStatus()
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(GlanceModifier.defaultWeight()) {
            Text(
                site.name,
                style =
                    TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.onSurface,
                    ),
                maxLines = 1,
            )
            val detail = listOfNotNull(site.providerId.displayName, site.latestDeployment?.branch).joinToString(" · ")
            Text(detail, style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
        }
        Text(
            LocalContext.current.getString(status.label),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = status.color),
        )
    }
}
