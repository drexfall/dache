package dev.dache.app.actions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.action_back
import dev.dache.app.resources.action_cancel
import dev.dache.app.resources.action_close
import dev.dache.app.resources.action_continue
import dev.dache.app.resources.action_destructive_warning
import dev.dache.app.resources.action_done_body
import dev.dache.app.resources.action_permission
import dev.dache.app.resources.action_review_changes
import dev.dache.app.resources.action_review_request
import dev.dache.app.resources.action_review_title
import dev.dache.app.resources.action_run
import dev.dache.app.ui.components.errorMessage
import dev.dache.app.ui.theme.LocalStatusColors
import dev.dache.core.model.ActionInput
import dev.dache.core.model.ActionInputType
import dev.dache.core.model.FieldChange
import org.jetbrains.compose.resources.stringResource

/** The whole action flow in one dialog: inputs, then a review of exactly what will be sent, then the result. */
@Composable
fun ActionDialog(
    step: ActionStep,
    viewModel: ActionsViewModel,
) {
    val title = "${step.action.label}: ${step.targetName}"
    when (step) {
        is ActionStep.Form ->
            AlertDialog(
                onDismissRequest = viewModel::dismiss,
                title = { Text(title) },
                text = {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        step.action.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        if (step.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        step.action.inputs.forEach { input ->
                            InputField(input, step.values[input.key].orEmpty()) { viewModel.setValue(input.key, it) }
                        }
                        step.error?.let { ErrorText(errorMessage(it)) }
                    }
                },
                confirmButton = {
                    Button(onClick = viewModel::review, enabled = !step.loading) {
                        Text(stringResource(Res.string.action_continue))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = viewModel::dismiss,
                    ) { Text(stringResource(Res.string.action_cancel)) }
                },
            )
        is ActionStep.Review -> ReviewDialog(step, title, viewModel)
        is ActionStep.Done ->
            AlertDialog(
                onDismissRequest = viewModel::dismiss,
                icon = {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = LocalStatusColors.current.success,
                    )
                },
                title = { Text(title) },
                text = { Text(stringResource(Res.string.action_done_body)) },
                confirmButton = {
                    TextButton(
                        onClick = viewModel::dismiss,
                    ) { Text(stringResource(Res.string.action_close)) }
                },
            )
    }
}

@Composable
private fun ReviewDialog(
    step: ActionStep.Review,
    title: String,
    viewModel: ActionsViewModel,
) {
    val preview = step.preview
    val destructive = step.action.destructive
    AlertDialog(
        onDismissRequest = { if (!step.running) viewModel.dismiss() },
        icon =
            if (destructive) {
                (
                    {
                        Icon(
                            Icons.Filled.Warning,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                )
            } else {
                null
            },
        title = { Text(stringResource(Res.string.action_review_title, title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (destructive) {
                    Text(
                        stringResource(Res.string.action_destructive_warning),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (preview.changes.isNotEmpty()) {
                    Text(stringResource(Res.string.action_review_changes), style = MaterialTheme.typography.labelLarge)
                    preview.changes.forEach { ChangeRow(it) }
                }
                Text(stringResource(Res.string.action_review_request), style = MaterialTheme.typography.labelLarge)
                Code(listOfNotNull("${preview.method} ${preview.url}", preview.body).joinToString("\n\n"))
                step.action.permission?.let {
                    Text(stringResource(Res.string.action_permission, it), style = MaterialTheme.typography.bodySmall)
                }
                if (step.running) LinearProgressIndicator(Modifier.fillMaxWidth())
                step.error?.let { ErrorText(errorMessage(it)) }
            }
        },
        confirmButton = {
            Button(
                onClick = viewModel::confirm,
                enabled = !step.running,
                colors =
                    if (destructive) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
            ) { Text(stringResource(Res.string.action_run, step.action.label)) }
        },
        dismissButton = {
            Row {
                if (step.action.inputs.isNotEmpty()) {
                    TextButton(onClick = viewModel::edit, enabled = !step.running) {
                        Text(stringResource(Res.string.action_back))
                    }
                }
                TextButton(onClick = viewModel::dismiss, enabled = !step.running) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        },
    )
}

/** `before → after`, with the new value emphasised; a value that doesn't change is shown once, muted. */
@Composable
private fun ChangeRow(change: FieldChange) {
    Column {
        Text(
            change.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (change.before == change.after) {
            Text(change.after, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.Center) {
                Text(change.before ?: "—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("→")
                Text(change.after, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun InputField(
    input: ActionInput,
    value: String,
    onChange: (String) -> Unit,
) {
    when {
        input.type == ActionInputType.BOOLEAN ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = value == "true", onCheckedChange = { onChange(it.toString()) })
                Text(input.label)
            }
        input.options.isNotEmpty() ->
            Column {
                Text(input.label, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    input.options.forEach { option ->
                        FilterChip(selected = value == option, onClick = { onChange(option) }, label = { Text(option) })
                    }
                }
            }
        else ->
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text(input.label) },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            if (input.type ==
                                ActionInputType.NUMBER
                            ) {
                                KeyboardType.Number
                            } else {
                                KeyboardType.Text
                            },
                    ),
                modifier = Modifier.fillMaxWidth(),
            )
    }
}

@Composable
private fun Code(text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(
            text,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(10.dp),
        )
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}
