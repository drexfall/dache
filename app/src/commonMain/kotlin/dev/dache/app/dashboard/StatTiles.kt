package dev.dache.app.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.stat_nothing_listed
import dev.dache.app.resources.state_collapsed
import dev.dache.app.resources.state_expanded
import dev.dache.app.ui.components.ExpandChevron
import dev.dache.app.ui.components.ExpandableBody
import dev.dache.app.ui.layout.AdaptiveGrid
import org.jetbrains.compose.resources.stringResource

/** One item behind a number, e.g. the site that is failing. [onClick] navigates inside the app. */
data class StatDetail(
    val label: String,
    val value: String? = null,
    val onClick: (() -> Unit)? = null,
)

/**
 * A headline number. Tapping expands it to list the items it counts. [problem] tints the tile so issues
 * stand out; the number itself still carries the meaning.
 */
data class Stat(
    val label: String,
    val value: String,
    val caption: String? = null,
    val problem: Boolean = false,
    val details: List<StatDetail> = emptyList(),
)

/** Two per row on phones, up to four on wide screens. */
@Composable
fun StatTiles(
    stats: List<Stat>,
    modifier: Modifier = Modifier,
) {
    AdaptiveGrid(
        items = stats.map { stat -> { StatTile(stat) } },
        minCellWidth = 150.dp,
        maxColumns = 4,
        spacing = 12.dp,
        modifier = modifier,
    )
}

@Composable
private fun StatTile(stat: Stat) {
    var expanded by rememberSaveable(stat.label) { mutableStateOf(false) }
    val description = listOfNotNull(stat.label, stat.value, stat.caption).joinToString(", ")
    val stateText = stringResource(if (expanded) Res.string.state_expanded else Res.string.state_collapsed)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (stat.problem) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
            ),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .semantics {
                        contentDescription = description
                        stateDescription = stateText
                    }.padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stat.label, style = MaterialTheme.typography.labelMedium)
                    Text(stat.value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    stat.caption?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ExpandChevron(expanded)
            }
            ExpandableBody(expanded) {
                Column(
                    Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (stat.details.isEmpty()) {
                        Text(stringResource(Res.string.stat_nothing_listed), style = MaterialTheme.typography.bodySmall)
                    }
                    stat.details.forEach { detail -> StatDetailRow(detail) }
                }
            }
        }
    }
}

@Composable
private fun StatDetailRow(detail: StatDetail) {
    val clickable = detail.onClick?.let { Modifier.clickable(role = Role.Button, onClick = it) } ?: Modifier
    // Tappable rows get a full 48dp target; plain ones stay compact.
    val minHeight = if (detail.onClick != null) 48.dp else 28.dp
    Row(clickable.fillMaxWidth().heightIn(min = minHeight), verticalAlignment = Alignment.CenterVertically) {
        Text(
            detail.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        detail.value?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
    }
}
