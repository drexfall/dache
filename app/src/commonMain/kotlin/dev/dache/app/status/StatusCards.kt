package dev.dache.app.status

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.status_all_ok
import dev.dache.app.resources.status_banner
import dev.dache.app.resources.status_checked
import dev.dache.app.resources.status_components
import dev.dache.app.resources.status_group_counts
import dev.dache.app.resources.status_incidents
import dev.dache.app.resources.status_level_degraded
import dev.dache.app.resources.status_level_maintenance
import dev.dache.app.resources.status_level_major
import dev.dache.app.resources.status_level_operational
import dev.dache.app.resources.status_level_partial
import dev.dache.app.resources.status_level_unknown
import dev.dache.app.resources.status_open_page
import dev.dache.app.resources.status_title
import dev.dache.app.resources.status_ungrouped
import dev.dache.app.ui.components.ExpandChevron
import dev.dache.app.ui.components.ExpandableBody
import dev.dache.app.ui.components.ExpandableHeader
import dev.dache.app.ui.components.ExternalLinkButton
import dev.dache.app.ui.components.Paged
import dev.dache.app.ui.components.relativeTime
import dev.dache.app.ui.theme.LocalChartColors
import dev.dache.app.ui.theme.LocalStatusColors
import dev.dache.core.model.ProviderStatus
import dev.dache.core.model.StatusComponent
import dev.dache.core.model.StatusIncident
import dev.dache.core.model.StatusLevel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A provider's public status: overall level, active incidents, then its components by group (regions, product
 * areas), worst groups first. Each group expands to its components, ten at a time, impaired ones first.
 */
@Composable
fun ProviderStatusCard(status: ProviderStatus) {
    val colors = LocalChartColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(Res.string.status_title, status.providerId.displayName),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.primaryText,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LevelChip(status.level)
                        status.description?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                        }
                    }
                    Text(
                        stringResource(Res.string.status_checked, relativeTime(status.fetchedAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.mutedText,
                    )
                }
                status.pageUrl?.let { ExternalLinkButton(stringResource(Res.string.status_open_page), it) }
            }
            if (status.incidents.isNotEmpty()) {
                Heading(stringResource(Res.string.status_incidents))
                Paged(status.incidents, key = "incidents-${status.providerId}", spacing = 8.dp) { IncidentRow(it) }
            }
            if (status.components.isNotEmpty()) {
                Heading(stringResource(Res.string.status_components))
                val groups =
                    status.components
                        .groupBy { it.group }
                        .toList()
                        .sortedWith(
                            compareByDescending<Pair<String?, List<StatusComponent>>> { (_, c) ->
                                c.count { it.level.isImpaired }
                            }.thenBy { it.first },
                        )
                Paged(groups, key = "groups-${status.providerId}") { (group, components) ->
                    GroupRow("${status.providerId.id}/$group", group, components)
                }
            }
        }
    }
}

/** Above the overview, one line per provider whose service isn't fully working. Tapping one opens its account. */
@Composable
fun StatusBanner(
    statuses: Collection<ProviderStatus>,
    onOpen: (ProviderStatus) -> Unit,
) {
    val impaired = statuses.filter { it.level.isImpaired }.sortedByDescending { it.level }
    if (impaired.isEmpty()) return
    val colors = LocalChartColors.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            impaired.forEachIndexed { index, status ->
                if (index > 0) HorizontalDivider(color = colors.grid)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            role = Role.Button,
                        ) { onOpen(status) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LevelChip(status.level)
                    Text(
                        stringResource(
                            Res.string.status_banner,
                            status.providerId.displayName,
                            status.description ?: stringResource(status.level.label()),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.primaryText,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val hit = status.components.count { it.level.isImpaired }
                    if (hit > 0) {
                        Text(
                            stringResource(Res.string.status_group_counts, hit, status.components.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.secondaryText,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupRow(
    key: String,
    group: String?,
    components: List<StatusComponent>,
) {
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    val colors = LocalChartColors.current
    val impaired = components.count { it.level.isImpaired }
    val worst =
        components.maxOf {
            it.level.takeUnless { level -> level == StatusLevel.UNKNOWN }
                ?: StatusLevel.OPERATIONAL
        }
    Column {
        ExpandableHeader(expanded = expanded, onToggle = { expanded = !expanded }) {
            Dot(worst)
            Text(
                group ?: stringResource(Res.string.status_ungrouped),
                modifier = Modifier.weight(1f).padding(start = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.primaryText,
            )
            Text(
                if (impaired == 0) {
                    stringResource(Res.string.status_all_ok, components.size)
                } else {
                    stringResource(Res.string.status_group_counts, impaired, components.size)
                },
                style = MaterialTheme.typography.labelMedium,
                color = colors.secondaryText,
            )
            ExpandChevron(expanded, Modifier.padding(start = 8.dp))
        }
        ExpandableBody(expanded) {
            val ordered =
                components.sortedWith(
                    compareByDescending<StatusComponent> {
                        it.level.isImpaired
                    }.thenByDescending { it.level }.thenBy { it.name },
                )
            Paged(
                ordered,
                key = "components-$key",
                modifier = Modifier.padding(start = 18.dp),
                spacing = 6.dp,
            ) { component ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Dot(component.level)
                    Text(
                        component.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(component.level.label()),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.secondaryText,
                    )
                }
            }
        }
    }
}

@Composable
private fun IncidentRow(incident: StatusIncident) {
    val colors = LocalChartColors.current
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LevelChip(incident.impact)
        Column(Modifier.weight(1f)) {
            Text(
                incident.name,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.primaryText,
                fontWeight = FontWeight.Medium,
            )
            Text(
                listOfNotNull(incident.status, incident.updatedAt?.let { relativeTime(it) }).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = colors.secondaryText,
            )
        }
        incident.url?.let { ExternalLinkButton("↗", it) }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = LocalChartColors.current.secondaryText)
}

/** A dot in the level's color with its name beside it, so the level never rests on color alone. */
@Composable
fun LevelChip(level: StatusLevel) {
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Dot(level)
        Text(
            stringResource(level.label()),
            style = MaterialTheme.typography.labelMedium,
            color = LocalChartColors.current.primaryText,
        )
    }
}

@Composable
private fun Dot(level: StatusLevel) {
    Box(Modifier.size(8.dp).background(level.color(), CircleShape))
}

@Composable
private fun StatusLevel.color(): Color {
    val status = LocalStatusColors.current
    return when (this) {
        StatusLevel.OPERATIONAL -> status.success
        StatusLevel.MAINTENANCE -> status.building
        StatusLevel.DEGRADED -> status.unknown
        StatusLevel.PARTIAL_OUTAGE, StatusLevel.MAJOR_OUTAGE -> status.failed
        StatusLevel.UNKNOWN -> status.cancelled
    }
}

private fun StatusLevel.label(): StringResource =
    when (this) {
        StatusLevel.OPERATIONAL -> Res.string.status_level_operational
        StatusLevel.MAINTENANCE -> Res.string.status_level_maintenance
        StatusLevel.DEGRADED -> Res.string.status_level_degraded
        StatusLevel.PARTIAL_OUTAGE -> Res.string.status_level_partial
        StatusLevel.MAJOR_OUTAGE -> Res.string.status_level_major
        StatusLevel.UNKNOWN -> Res.string.status_level_unknown
    }
