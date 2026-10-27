package dev.dache.app.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dache.app.ui.theme.LocalChartColors

data class BarItem(
    val label: String,
    val value: Double,
)

/**
 * One series of horizontal bars for comparing magnitudes. Each bar carries its value at the tip, so every
 * value is always on screen and no tooltip is needed.
 */
@Composable
fun HorizontalBarChart(
    items: List<BarItem>,
    color: Color,
    valueFormatter: (Double) -> String,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    onSelect: (Int?) -> Unit = {},
) {
    val colors = LocalChartColors.current
    val textMeasurer = rememberTextMeasurer()
    val valueStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.primaryText)
    val max = items.maxOfOrNull { it.value }?.takeIf { it > 0 } ?: 1.0
    val widestValue = items.maxOfOrNull { textMeasurer.measure(valueFormatter(it.value), valueStyle).size.width } ?: 0
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { index, item ->
            val isSelected = index == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp)
                    .background(if (isSelected) colors.grid else Color.Transparent, RoundedCornerShape(6.dp))
                    .selectable(selected = isSelected, role = Role.Button) { onSelect(index.takeUnless { isSelected }) }
                    .clearAndSetSemantics {
                        contentDescription = "${item.label}: ${valueFormatter(item.value)}"
                        this.selected = isSelected
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.label,
                    modifier = Modifier.width(128.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Canvas(Modifier.weight(1f).height(18.dp)) {
                    val labelGap = 6.dp.toPx()
                    val track = (size.width - widestValue - labelGap).coerceAtLeast(0f)
                    val length = (item.value / max).toFloat() * track
                    val barTop = 1.dp.toPx()
                    drawLine(colors.baseline, Offset(0f, 0f), Offset(0f, size.height), 1f)
                    if (length >
                        0f
                    ) {
                        drawPath(rightRoundedRect(0f, barTop, length, size.height - barTop, 4.dp.toPx()), color)
                    }
                    val label = textMeasurer.measure(valueFormatter(item.value), valueStyle)
                    drawText(label, topLeft = Offset(length + labelGap, (size.height - label.size.height) / 2f))
                }
            }
        }
    }
}
