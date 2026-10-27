package dev.dache.app.dashboard.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.detail_more
import dev.dache.app.resources.link_open_build
import dev.dache.app.ui.components.ExternalLinkIconButton
import dev.dache.app.ui.components.StatusChip
import dev.dache.app.ui.theme.LocalChartColors
import dev.dache.core.dashboard.StatusEvent
import org.jetbrains.compose.resources.stringResource

/** Builds behind a pinned mark: status, what, when (and how long), with a link out to each one. */
@Composable
fun EventList(
    events: List<StatusEvent>,
    whenText: (StatusEvent) -> String,
    limit: Int = DEFAULT_LIMIT,
) {
    val colors = LocalChartColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        events.take(limit).forEach { event ->
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusChip(event.status)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        event.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(whenText(event), style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                }
                event.webUrl?.let { url ->
                    ExternalLinkIconButton(url, stringResource(Res.string.link_open_build, event.title))
                }
            }
        }
        if (events.size > limit) {
            Text(
                stringResource(Res.string.detail_more, events.size - limit),
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryText,
            )
        }
    }
}

/** Shown under a chart until the user pins something, so the interaction is discoverable. */
@Composable
fun ChartHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = LocalChartColors.current.mutedText)
}

private const val DEFAULT_LIMIT = 8
