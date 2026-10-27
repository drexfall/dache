package dev.dache.app.ui.chart

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.dache.app.ui.theme.LocalChartColors
import kotlin.math.max
import kotlin.math.roundToInt

/** One tooltip line: a short line-key in the series color, the value (strong), then the series name. */
data class TooltipRow(
    val color: Color,
    val value: String,
    val label: String,
)

/** Floating readout centred on [anchorX] and clamped inside the chart's width. */
@Composable
fun ChartTooltip(
    anchorX: Float,
    title: String,
    rows: List<TooltipRow>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalChartColors.current
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border),
                shadowElevation = 4.dp,
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(title, style = MaterialTheme.typography.labelSmall, color = colors.secondaryText)
                    rows.forEach { row ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(Modifier.size(width = 12.dp, height = 2.dp).background(row.color))
                            Text(
                                row.value,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.primaryText,
                            )
                            Text(row.label, style = MaterialTheme.typography.labelMedium, color = colors.secondaryText)
                        }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val maxX = max(0, constraints.maxWidth - placeable.width)
        layout(constraints.maxWidth, placeable.height) {
            placeable.place((anchorX - placeable.width / 2f).roundToInt().coerceIn(0, maxX), 0)
        }
    }
}
