package dev.dache.app.boards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.board_name_label
import dev.dache.app.resources.editor_board_section
import dev.dache.app.resources.editor_nothing_selected
import dev.dache.app.resources.widget_field_aggregation
import dev.dache.app.resources.widget_field_chart
import dev.dache.app.resources.widget_field_height
import dev.dache.app.resources.widget_field_position
import dev.dache.app.resources.widget_field_range
import dev.dache.app.resources.widget_field_scope
import dev.dache.app.resources.widget_field_size
import dev.dache.app.resources.widget_field_title
import dev.dache.app.resources.widget_field_title_hint
import dev.dache.app.resources.widget_field_width
import dev.dache.app.resources.widget_height_rows
import dev.dache.app.resources.widget_move_earlier
import dev.dache.app.resources.widget_move_later
import dev.dache.app.resources.widget_narrower
import dev.dache.app.resources.widget_position_earlier
import dev.dache.app.resources.widget_position_later
import dev.dache.app.resources.widget_range_days
import dev.dache.app.resources.widget_remove
import dev.dache.app.resources.widget_scope_all
import dev.dache.app.resources.widget_scope_provider
import dev.dache.app.resources.widget_shorter
import dev.dache.app.resources.widget_taller
import dev.dache.app.resources.widget_wider
import dev.dache.app.resources.widget_width_columns
import dev.dache.app.ui.theme.LocalChartColors
import dev.dache.core.boards.Aggregation
import dev.dache.core.boards.Board
import dev.dache.core.boards.DatapointShape
import dev.dache.core.boards.Widget
import dev.dache.core.boards.WidgetChart
import dev.dache.core.boards.WidgetScope
import dev.dache.core.dashboard.DashboardState
import org.jetbrains.compose.resources.stringResource

/**
 * The selected widget's settings, each a row of choices rather than a menu, so every option is in view. With
 * nothing selected it shows the board's name and how to get started.
 */
@Composable
fun WidgetProperties(
    state: BoardEditorState,
    dashboard: DashboardState,
    boardName: String,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val widget = state.selected
        if (widget == null) {
            BoardSection(boardName, onRename)
        } else {
            WidgetSection(widget, state, dashboard)
        }
    }
}

@Composable
private fun BoardSection(
    boardName: String,
    onRename: (String) -> Unit,
) {
    var name by remember(boardName) { mutableStateOf(boardName) }
    Text(stringResource(Res.string.editor_board_section), style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        value = name,
        onValueChange = {
            name = it.take(Board.MAX_NAME_LENGTH)
            if (name.isNotBlank()) onRename(name)
        },
        label = { Text(stringResource(Res.string.board_name_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        stringResource(Res.string.editor_nothing_selected),
        style = MaterialTheme.typography.bodySmall,
        color = LocalChartColors.current.secondaryText,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetSection(
    widget: Widget,
    state: BoardEditorState,
    dashboard: DashboardState,
) {
    val title = widget.displayTitle()
    val change = { next: Widget -> state.update(next) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            // With a title of its own, say what the widget shows; otherwise the title already does.
            if (widget.title.isNotBlank()) {
                Text(
                    stringResource(widget.datapoint.label),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalChartColors.current.secondaryText,
                )
            }
        }
        IconButton(onClick = { state.remove(widget.id) }) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.widget_remove, title))
        }
    }
    OutlinedTextField(
        value = widget.title,
        onValueChange = { change(widget.copy(title = it)) },
        label = { Text(stringResource(Res.string.widget_field_title)) },
        placeholder = {
            Text(
                stringResource(Res.string.widget_field_title_hint, stringResource(widget.datapoint.label)),
            )
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Field(stringResource(Res.string.widget_field_chart)) {
        Chips(widget.datapoint.charts, widget.chart, { stringResource(it.label) }) {
            // A widget the user hasn't resized takes the new chart's own height.
            change(widget.copy(chart = it))
        }
    }
    Field(stringResource(Res.string.widget_field_scope)) {
        val scopes = scopeOptions(dashboard)
        Chips(scopes.map { it.first }, widget.scope, { scope -> scopes.first { it.first == scope }.second }) {
            change(widget.copy(scope = it))
        }
    }
    if (widget.datapoint.shape == DatapointShape.SERIES) {
        Field(stringResource(Res.string.widget_field_range)) {
            Chips(Widget.DAY_RANGES, widget.days, { stringResource(Res.string.widget_range_days, it) }) {
                change(widget.copy(days = it))
            }
        }
    }
    if (widget.datapoint.shape == DatapointShape.SERIES && widget.chart == WidgetChart.STAT) {
        Field(stringResource(Res.string.widget_field_aggregation)) {
            Chips(Aggregation.entries, widget.effectiveAggregation, { stringResource(it.label) }) {
                change(widget.copy(aggregation = it))
            }
        }
    }
    Field(stringResource(Res.string.widget_field_size)) {
        Stepper(
            label = stringResource(Res.string.widget_field_width),
            value = stringResource(Res.string.widget_width_columns, widget.span, Widget.MAX_SPAN),
            less =
                stringResource(Res.string.widget_narrower, title) to
                    { change(widget.copy(span = widget.span - 1)) }.takeIf { widget.span > 1 },
            more =
                stringResource(Res.string.widget_wider, title) to
                    { change(widget.copy(span = widget.span + 1)) }.takeIf { widget.span < Widget.MAX_SPAN },
        )
        Stepper(
            label = stringResource(Res.string.widget_field_height),
            value = stringResource(Res.string.widget_height_rows, widget.effectiveHeight),
            less =
                stringResource(Res.string.widget_shorter, title) to
                    { change(widget.copy(height = widget.effectiveHeight - 1)) }.takeIf { widget.effectiveHeight > 1 },
            more =
                stringResource(Res.string.widget_taller, title) to
                    { change(widget.copy(height = widget.effectiveHeight + 1)) }
                        .takeIf { widget.effectiveHeight < Widget.MAX_HEIGHT },
        )
    }
    Field(stringResource(Res.string.widget_field_position)) {
        val index = state.widgets.indexOfFirst { it.id == widget.id }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val earlier = stringResource(Res.string.widget_move_earlier, title)
            val later = stringResource(Res.string.widget_move_later, title)
            OutlinedButton(
                onClick = { state.move(widget.id, -1) },
                enabled = index > 0,
                modifier = Modifier.semantics { contentDescription = earlier },
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                Text(stringResource(Res.string.widget_position_earlier))
            }
            OutlinedButton(
                onClick = { state.move(widget.id, 1) },
                enabled = index < state.widgets.lastIndex,
                modifier = Modifier.semantics { contentDescription = later },
            ) {
                Text(stringResource(Res.string.widget_position_later))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}

/** A value with a button either side; a null action disables its button, e.g. at the smallest size. */
@Composable
private fun Stepper(
    label: String,
    value: String,
    less: Pair<String, (() -> Unit)?>,
    more: Pair<String, (() -> Unit)?>,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        IconButton(
            onClick = { less.second?.invoke() },
            enabled = less.second != null,
            modifier = Modifier.semantics { contentDescription = less.first },
        ) { Text("−", style = MaterialTheme.typography.titleMedium) }
        Text(
            value,
            modifier = Modifier.widthIn(min = 96.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        IconButton(
            onClick = { more.second?.invoke() },
            enabled = more.second != null,
            modifier = Modifier.semantics { contentDescription = more.first },
        ) { Icon(Icons.Filled.Add, contentDescription = null) }
    }
}

@Composable
private fun Field(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        content()
    }
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
