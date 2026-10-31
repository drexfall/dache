package dev.dache.app.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dache.app.ui.theme.LocalChartColors

data class UptimeDay(
    val label: String,
    val percent: Double,
)

/**
 * One row of day cells per monitor. Emphasis form: days with any downtime are critical red, clean days a
 * recessive gray. Hovering or tapping a cell replaces the row's summary with that day's value.
 */
@Composable
fun UptimeStrip(
    name: String,
    summary: String,
    days: List<UptimeDay>,
    dayFormatter: (UptimeDay) -> String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    onSelect: (Int?) -> Unit = {},
) {
    val colors = LocalChartColors.current
    var widthPx by remember { mutableStateOf(0f) }
    var hovered by remember { mutableStateOf<Int?>(null) }

    fun cellAt(x: Float): Int? =
        if (days.isEmpty() || widthPx <= 0f) null else (x / widthPx * days.size).toInt().coerceIn(0, days.lastIndex)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(
                name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = colors.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                (hovered ?: selected)?.let { dayFormatter(days[it]) } ?: summary,
                style = MaterialTheme.typography.labelMedium,
                color = colors.secondaryText,
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(20.dp)
                .onSizeChanged { widthPx = it.width.toFloat() }
                .trackPointerX(
                    onX = { x -> hovered = x?.let { cellAt(it) } },
                    onTap = { x -> cellAt(x)?.let { index -> onSelect(index.takeUnless { it == selected }) } },
                ).semantics { this.contentDescription = contentDescription },
        ) {
            if (days.isEmpty()) return@Canvas
            val focus = hovered ?: selected
            val gap = 2.dp.toPx()
            val cell = (size.width - gap * (days.size - 1)) / days.size
            days.forEachIndexed { index, day ->
                val color = if (day.percent < FULL_UPTIME) colors.critical else colors.quiet
                drawRoundRect(
                    color = if (focus == null || focus == index) color else color.copy(alpha = DIMMED_ALPHA),
                    topLeft = Offset(index * (cell + gap), 0f),
                    size = Size(cell, size.height),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }
    }
}

private const val FULL_UPTIME = 100.0

/** Other cells fade while one is hovered or pinned, so the focused day stands out. */
private const val DIMMED_ALPHA = 0.55f
