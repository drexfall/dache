package dev.dache.app.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.action_cancel
import dev.dache.app.resources.action_connect
import dev.dache.app.resources.action_create_token
import dev.dache.app.resources.add_account_title
import dev.dache.app.resources.demo_token_hint
import dev.dache.app.resources.scope_cloudflare
import dev.dache.app.resources.scope_digitalocean
import dev.dache.app.resources.scope_github
import dev.dache.app.resources.scope_netlify
import dev.dache.app.resources.scope_uptimerobot
import dev.dache.app.resources.scope_vercel
import dev.dache.app.resources.setting_optional_label
import dev.dache.app.resources.token_label
import dev.dache.app.ui.components.ExternalLinkButton
import dev.dache.app.ui.components.errorMessage
import dev.dache.app.ui.components.secureDialogProperties
import dev.dache.app.ui.components.secureSheetProperties
import dev.dache.app.ui.layout.FormFactor
import dev.dache.app.ui.layout.LocalFormFactor
import dev.dache.core.model.ProviderId
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom sheet on phones (reachable, keyboard-friendly), a centred dialog on desktop. Both are secure windows on
 * Android, so the token can't be captured by screenshots, screen recording or the recents thumbnail.
 */
@Composable
fun AddAccountFlow(
    state: AddAccountState,
    onProviderSelected: (ProviderId) -> Unit,
    onTokenChanged: (String) -> Unit,
    onSettingChanged: (key: String, value: String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val form: @Composable () -> Unit = {
        AddAccountForm(state, onProviderSelected, onTokenChanged, onSettingChanged, onSubmit)
    }
    if (LocalFormFactor.current == FormFactor.COMPACT) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            properties = secureSheetProperties(),
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(Res.string.add_account_title), style = MaterialTheme.typography.titleLarge)
                form()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
                    Button(
                        onClick = onSubmit,
                        enabled = state.canSubmit,
                    ) { Text(stringResource(Res.string.action_connect)) }
                }
            }
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.add_account_title)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { form() } },
            confirmButton = {
                TextButton(
                    onClick = onSubmit,
                    enabled = state.canSubmit,
                ) { Text(stringResource(Res.string.action_connect)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
            },
            properties = secureDialogProperties(),
        )
    }
}

@Composable
private fun AddAccountForm(
    state: AddAccountState,
    onProviderSelected: (ProviderId) -> Unit,
    onTokenChanged: (String) -> Unit,
    onSettingChanged: (key: String, value: String) -> Unit,
    onSubmit: () -> Unit,
) {
    val tokenIme = if (state.settings.isEmpty()) ImeAction.Done else ImeAction.Next
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProviderId.entries.forEach { provider ->
                FilterChip(
                    selected = provider == state.provider,
                    onClick = { onProviderSelected(provider) },
                    label = { Text(provider.displayName) },
                )
            }
        }
        Text(stringResource(state.provider.scopeHint()), style = MaterialTheme.typography.bodyMedium)
        ExternalLinkButton(
            text = stringResource(Res.string.action_create_token, state.provider.displayName),
            url = state.provider.tokenPageUrl,
        )
        OutlinedTextField(
            value = state.token,
            onValueChange = onTokenChanged,
            label = { Text(stringResource(Res.string.token_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = tokenIme),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            isError = state.error != null,
            supportingText = state.error?.let { { Text(errorMessage(it)) } },
            modifier = Modifier.fillMaxWidth(),
        )
        state.settings.forEachIndexed { index, setting ->
            val last = index == state.settings.lastIndex
            OutlinedTextField(
                value = state.settingValues[setting.key].orEmpty(),
                onValueChange = { onSettingChanged(setting.key, it) },
                label = {
                    Text(
                        if (setting.required) {
                            setting.label
                        } else {
                            stringResource(Res.string.setting_optional_label, setting.label)
                        },
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = if (last) ImeAction.Done else ImeAction.Next),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                supportingText = setting.hint?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        TokenChecklist(state.check, state.provider.displayName, state.permissions)
        if (state.isValidating) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.sampleData) {
            Text(
                stringResource(Res.string.demo_token_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Least-privilege guidance shown before the user creates a token. */
private fun ProviderId.scopeHint(): StringResource =
    when (this) {
        ProviderId.NETLIFY -> Res.string.scope_netlify
        ProviderId.VERCEL -> Res.string.scope_vercel
        ProviderId.CLOUDFLARE -> Res.string.scope_cloudflare
        ProviderId.GITHUB -> Res.string.scope_github
        ProviderId.DIGITALOCEAN -> Res.string.scope_digitalocean
        ProviderId.UPTIMEROBOT -> Res.string.scope_uptimerobot
    }
