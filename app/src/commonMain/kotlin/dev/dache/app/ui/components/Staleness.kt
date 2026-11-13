package dev.dache.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.updated_at
import dev.dache.app.resources.updated_stale
import dev.dache.core.alerts.AlertSettings
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * How old data may be before pages say so: one background-check interval while checks are on, since a check
 * should have replaced it by then, and an hour otherwise.
 */
fun staleAfter(settings: AlertSettings): Duration =
    if (settings.enabled) settings.intervalMinutes.minutes else DEFAULT_STALE_AFTER

val DEFAULT_STALE_AFTER: Duration = 1.hours

val LocalStaleAfter = compositionLocalOf { DEFAULT_STALE_AFTER }

/** Whether [lastUpdated] is older than the page's staleness threshold. */
fun isStale(
    lastUpdated: Instant,
    staleAfter: Duration,
    now: Instant = Clock.System.now(),
): Boolean = now - lastUpdated > staleAfter

/**
 * "Updated 3 h ago", beside the refresh control. Quiet while the data is recent; in the warning tone, and marked
 * out of date for screen readers, once it is older than [LocalStaleAfter].
 */
@Composable
fun UpdatedChip(
    lastUpdated: Instant?,
    modifier: Modifier = Modifier,
) {
    val updated = lastUpdated ?: return
    val stale = isStale(updated, LocalStaleAfter.current)
    val staleText = stringResource(Res.string.updated_stale)
    Text(
        stringResource(Res.string.updated_at, relativeTime(updated)),
        style = MaterialTheme.typography.labelMedium,
        color = if (stale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier =
            modifier
                .padding(horizontal = 4.dp)
                .then(if (stale) Modifier.semantics { stateDescription = staleText } else Modifier),
    )
}

/** Cached resources shown under an account's error card: still there, but visibly not current. */
@Composable
fun Dimmed(
    dim: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = if (dim) Modifier.alpha(DIM_ALPHA) else Modifier,
        verticalArrangement =
            androidx.compose.foundation.layout.Arrangement
                .spacedBy(16.dp),
        content = content,
    )
}

private const val DIM_ALPHA = 0.6f
