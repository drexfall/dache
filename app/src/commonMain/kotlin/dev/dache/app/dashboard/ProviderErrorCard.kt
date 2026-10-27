package dev.dache.app.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dache.app.navigation.LocalAppNavigator
import dev.dache.app.resources.Res
import dev.dache.app.resources.action_fix_token
import dev.dache.app.resources.action_retry
import dev.dache.app.resources.error_help_rate_limited
import dev.dache.app.resources.error_help_unauthorized
import dev.dache.app.resources.error_help_unknown
import dev.dache.app.resources.link_go_to_account
import dev.dache.app.resources.provider_error
import dev.dache.app.ui.components.ExpandableBody
import dev.dache.app.ui.components.ExpandableHeader
import dev.dache.app.ui.components.errorMessage
import dev.dache.core.dashboard.AccountResult
import dev.dache.core.model.ProviderError
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A failed account. Expands to explain what to do, with retry and the right next step. */
@Composable
fun ProviderErrorCard(
    result: AccountResult,
    onRetry: () -> Unit,
) {
    val error = result.error ?: return
    val navigator = LocalAppNavigator.current
    var expanded by rememberSaveable(result.account.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            ExpandableHeader(expanded = expanded, onToggle = { expanded = !expanded }) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(
                            Res.string.provider_error,
                            result.account.providerId.displayName,
                            result.account.displayName,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(errorMessage(error), style = MaterialTheme.typography.bodyMedium)
                }
            }
            ExpandableBody(expanded) {
                Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(error.helpText()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
                        if (error is ProviderError.Unauthorized || error is ProviderError.MissingScope) {
                            OutlinedButton(onClick = navigator::openManageAccounts) {
                                Text(stringResource(Res.string.action_fix_token))
                            }
                        } else {
                            OutlinedButton(onClick = { navigator.openAccount(result.account.id) }) {
                                Text(
                                    stringResource(
                                        Res.string.link_go_to_account,
                                        result.account.providerId.displayName,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ProviderError.helpText(): StringResource =
    when (this) {
        is ProviderError.Unauthorized, is ProviderError.MissingScope -> Res.string.error_help_unauthorized
        is ProviderError.RateLimited -> Res.string.error_help_rate_limited
        else -> Res.string.error_help_unknown
    }
