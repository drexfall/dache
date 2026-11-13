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
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.dache.android.R
import dev.dache.core.dashboard.DashboardState
import kotlin.time.Duration

/** Four problem counters, the same ones at the top of the overview screen. */
class SummaryWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val dashboard = context.loadedDashboard()
        val staleAfter = context.staleAfter()
        provideContent {
            val state by dashboard.state.collectAsState()
            GlanceTheme { SummaryContent(state, staleAfter) }
        }
    }
}

class SummaryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SummaryWidget()
}

@Composable
private fun SummaryContent(
    state: DashboardState,
    staleAfter: Duration,
) {
    val context = LocalContext.current
    WidgetFrame {
        WidgetTitle(R.string.widget_title_status)
        if (state.accounts.isEmpty()) {
            WidgetCaption(context.getString(R.string.widget_empty))
            return@WidgetFrame
        }
        val summary = state.summary
        Row(GlanceModifier.defaultWeight()) {
            Counter(summary.failingDeploys, R.string.summary_failing_deploys, GlanceModifier.defaultWeight())
            Counter(summary.downMonitors, R.string.summary_down_monitors, GlanceModifier.defaultWeight())
        }
        Row(GlanceModifier.defaultWeight()) {
            Counter(summary.expiringDomains, R.string.summary_expiring_domains, GlanceModifier.defaultWeight())
            Counter(summary.failedCiRuns, R.string.summary_failed_ci, GlanceModifier.defaultWeight())
        }
        val errors = state.results.count { it.error != null }
        if (errors > 0) WidgetCaption(context.getString(R.string.widget_account_errors, errors))
        UpdatedCaption(state.lastUpdated, staleAfter)
    }
}

@Composable
private fun Counter(
    count: Int,
    label: Int,
    modifier: GlanceModifier,
) {
    val color = if (count > 0) WidgetStatusColors.failed else WidgetStatusColors.success
    Column(modifier) {
        Text(count.toString(), style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color))
        Text(
            LocalContext.current.getString(label),
            style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(4.dp))
    }
}
