package dev.dache.app.boards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.action_cancel
import dev.dache.app.resources.widget_add
import dev.dache.app.resources.widget_edit_title
import dev.dache.app.resources.widget_field_aggregation
import dev.dache.app.resources.widget_field_chart
import dev.dache.app.resources.widget_field_data
import dev.dache.app.resources.widget_field_range
import dev.dache.app.resources.widget_field_scope
import dev.dache.app.resources.widget_field_title
import dev.dache.app.resources.widget_field_title_hint
import dev.dache.app.resources.widget_field_width
import dev.dache.app.resources.widget_new_title
import dev.dache.app.resources.widget_preview
import dev.dache.app.resources.widget_range_days
import dev.dache.app.resources.widget_save
import dev.dache.app.resources.widget_scope_all
import dev.dache.app.resources.widget_scope_provider
import dev.dache.app.resources.widget_width_columns
import dev.dache.core.boards.Aggregation
import dev.dache.core.boards.Datapoint
import dev.dache.core.boards.DatapointShape
import dev.dache.core.boards.Widget
import dev.dache.core.boards.WidgetChart
import dev.dache.core.boards.WidgetData
import dev.dache.core.boards.WidgetScope
import dev.dache.core.dashboard.DashboardState
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/**
 * Picks what a widget shows and how, with a live preview from the user's own data underneath, so what they see
 * while choosing is what the dashboard will show. [isNew] only changes the wording.
 */
@Composable
fun WidgetEditorDialog(
    initial: Widget,
    isNew: Boolean,
    state: DashboardState,
    onSave: (Widget) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(initial.normalized()) }
    val now = remember(state.lastUpdated) { Clock.System.now() }
    val preview = remember(draft, state, now) { WidgetData.evaluate(draft, state, now) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) Res.string.widget_new_title else Res.string.widget_edit_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WidgetForm(draft, state) { draft = it.normalized() }
                HorizontalDivider()
                Text(stringResource(Res.string.widget_preview), style = MaterialTheme.typography.labelLarge)
                WidgetCard(draft, preview)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(draft)
                onDismiss()
            }) { Text(stringResource(if (isNew) Res.string.widget_add else Res.string.widget_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetForm(
    widget: Widget,
    state: DashboardState,
    onChange: (Widget) -> Unit,
) {
    Field(stringResource(Res.string.widget_field_data)) {
        Choice(
            selected = stringResource(widget.datapoint.label),
            options = Datapoint.entries.map { it to stringResource(it.label) },
            onSelect = { datapoint -> onChange(widget.copy(datapoint = datapoint, aggregation = null)) },
        )
    }
    Field(stringResource(Res.string.widget_field_chart)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            widget.datapoint.charts.forEach { chart ->
                FilterChip(
                    selected = widget.chart == chart,
                    onClick = { onChange(widget.copy(chart = chart)) },
                    label = { Text(stringResource(chart.label)) },
                )
            }
        }
    }
    Field(stringResource(Res.string.widget_field_scope)) {
        val scopes = scopeOptions(state)
        Choice(
            selected = scopes.firstOrNull { it.first == widget.scope }?.second ?: scopes.first().second,
            options = scopes,
            onSelect = { onChange(widget.copy(scope = it)) },
        )
    }
    if (widget.datapoint.shape == DatapointShape.SERIES) {
        Field(stringResource(Res.string.widget_field_range)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Widget.DAY_RANGES.forEach { days ->
                    FilterChip(
                        selected = widget.days == days,
                        onClick = { onChange(widget.copy(days = days)) },
                        label = { Text(stringResource(Res.string.widget_range_days, days)) },
                    )
                }
            }
        }
        if (widget.chart == WidgetChart.STAT) {
            Field(stringResource(Res.string.widget_field_aggregation)) {
                Choice(
                    selected = stringResource(widget.effectiveAggregation.label),
                    options = Aggregation.entries.map { it to stringResource(it.label) },
                    onSelect = { onChange(widget.copy(aggregation = it)) },
                )
            }
        }
    }
    Field(stringResource(Res.string.widget_field_width)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (span in 1..Widget.MAX_SPAN) {
                FilterChip(
                    selected = widget.span == span,
                    onClick = { onChange(widget.copy(span = span)) },
                    label = { Text(span.toString()) },
                )
            }
        }
        Text(
            stringResource(Res.string.widget_width_columns, widget.span, Widget.MAX_SPAN),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    OutlinedTextField(
        value = widget.title,
        onValueChange = { onChange(widget.copy(title = it)) },
        label = { Text(stringResource(Res.string.widget_field_title)) },
        placeholder = {
            Text(stringResource(Res.string.widget_field_title_hint, stringResource(widget.datapoint.label)))
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Every account together, each provider's accounts, then each account on its own. */
@Composable
private fun scopeOptions(state: DashboardState): List<Pair<WidgetScope, String>> {
    val all = listOf(WidgetScope() to stringResource(Res.string.widget_scope_all))
    val providers =
        state.accounts
            .map { it.providerId }
            .distinct()
            .sortedBy { it.displayName.lowercase() }
            .map { WidgetScope(providerId = it.id) to stringResource(Res.string.widget_scope_provider, it.displayName) }
    val accounts =
        state.accounts
            .sortedBy { "${it.providerId.displayName} ${it.displayName}".lowercase() }
            .map { WidgetScope(accountId = it.id) to "${it.providerId.displayName} · ${it.displayName}" }
    return all + providers + accounts
}

@Composable
private fun Field(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        content()
    }
}

@Composable
private fun <T> Choice(
    selected: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(value)
                        open = false
                    },
                )
            }
        }
    }
}
