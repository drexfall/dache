package dev.dache.android.widget

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.dache.android.MainActivity
import dev.dache.android.R
import dev.dache.core.model.DeployStatus
import dev.dache.core.model.MonitorStatus
import kotlin.time.Duration
import kotlin.time.Instant

/** Widget status colors, matching the app's chips. Labels always carry a symbol too. */
internal object WidgetStatusColors {
    val success = ColorProvider(day = Color(0xFF1B7F3B), night = Color(0xFF7BD88F))
    val building = ColorProvider(day = Color(0xFF1D5FBF), night = Color(0xFF8AB4F8))
    val failed = ColorProvider(day = Color(0xFFB3261E), night = Color(0xFFF2B8B5))
    val neutral = ColorProvider(day = Color(0xFF5F6368), night = Color(0xFFBDC1C6))
}

internal data class WidgetStatus(
    @param:StringRes val label: Int,
    val color: ColorProvider,
)

internal fun DeployStatus?.widgetStatus(): WidgetStatus =
    when (this) {
        DeployStatus.SUCCESS -> WidgetStatus(R.string.status_success, WidgetStatusColors.success)
        DeployStatus.BUILDING -> WidgetStatus(R.string.status_building, WidgetStatusColors.building)
        DeployStatus.FAILED -> WidgetStatus(R.string.status_failed, WidgetStatusColors.failed)
        DeployStatus.CANCELLED -> WidgetStatus(R.string.status_cancelled, WidgetStatusColors.neutral)
        DeployStatus.UNKNOWN, null -> WidgetStatus(R.string.status_unknown, WidgetStatusColors.neutral)
    }

internal fun MonitorStatus.widgetStatus(): WidgetStatus =
    when (this) {
        MonitorStatus.UP -> WidgetStatus(R.string.status_up, WidgetStatusColors.success)
        MonitorStatus.DOWN -> WidgetStatus(R.string.status_down, WidgetStatusColors.failed)
        MonitorStatus.PAUSED -> WidgetStatus(R.string.status_paused, WidgetStatusColors.neutral)
        MonitorStatus.UNKNOWN -> WidgetStatus(R.string.status_unknown, WidgetStatusColors.neutral)
    }

/** Common frame: rounded background, tap opens the app. */
@Composable
internal fun WidgetFrame(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .cornerRadius(16.dp)
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
        content = content,
    )
}

@Composable
internal fun WidgetTitle(
    @StringRes title: Int,
) {
    Text(
        LocalContext.current.getString(title),
        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GlanceTheme.colors.onSurface),
        modifier = GlanceModifier.padding(bottom = 6.dp),
    )
}

@Composable
internal fun WidgetCaption(text: String) {
    Text(text, style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant), maxLines = 2)
}

/** "Updated 3 h ago"; in the failed tone once the data is older than [staleAfter], like the app's pages. */
@Composable
internal fun UpdatedCaption(
    lastUpdated: Instant?,
    staleAfter: Duration,
) {
    val updated = lastUpdated ?: return
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val relative = DateUtils.getRelativeTimeSpanString(updated.toEpochMilliseconds(), now, DateUtils.MINUTE_IN_MILLIS)
    val stale = Instant.fromEpochMilliseconds(now) - updated > staleAfter
    val color = if (stale) WidgetStatusColors.failed else GlanceTheme.colors.onSurfaceVariant
    Text(
        context.getString(R.string.widget_updated, relative),
        style = TextStyle(fontSize = 12.sp, color = color),
        maxLines = 2,
    )
}
