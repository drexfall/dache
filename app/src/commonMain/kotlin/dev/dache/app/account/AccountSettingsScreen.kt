package dev.dache.app.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dache.app.accounts.TokenCheckState
import dev.dache.app.accounts.TokenChecklist
import dev.dache.app.dashboard.DetailLine
import dev.dache.app.navigation.Breadcrumbs
import dev.dache.app.navigation.Destination
import dev.dache.app.navigation.trail
import dev.dache.app.resources.Res
import dev.dache.app.resources.action_check_again
import dev.dache.app.resources.action_create_token
import dev.dache.app.resources.breadcrumb_settings
import dev.dache.app.resources.detail_account
import dev.dache.app.resources.detail_provider
import dev.dache.app.resources.settings_connection
import dev.dache.app.resources.settings_token
import dev.dache.app.ui.components.ExternalLinkButton
import dev.dache.app.ui.layout.PageScaffold
import dev.dache.core.dashboard.DashboardState
import dev.dache.core.model.AccountSetting
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.TokenPermission
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A connected account's settings: what it was connected with, and the same token checklist as when connecting,
 * run again with the stored token each time the page opens.
 */
@Composable
fun AccountSettingsScreen(
    destination: Destination.AccountSettings,
    account: ConnectedAccount,
    settings: List<AccountSetting>,
    permissions: List<TokenPermission>,
    state: DashboardState,
    check: TokenCheckState,
    onCheck: () -> Unit,
    onRefresh: () -> Unit,
    onOpenMenu: (() -> Unit)?,
    onNavigate: (Destination) -> Unit,
) {
    LaunchedEffect(account.id) { onCheck() }
    val provider = account.providerId.displayName
    PageScaffold(
        title = stringResource(Res.string.breadcrumb_settings),
        subtitle = listOf(provider, account.displayName).joinToString(" · "),
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        onOpenMenu = onOpenMenu,
        header = { Breadcrumbs(destination.trail, state, onNavigate) },
    ) {
        SettingsCard(Res.string.settings_connection) {
            DetailLine(stringResource(Res.string.detail_provider), provider)
            DetailLine(stringResource(Res.string.detail_account), account.displayName)
            settings.forEach { setting ->
                account.settings[setting.key]?.let { DetailLine(setting.label, it) }
            }
        }
        SettingsCard(Res.string.settings_token) {
            TokenChecklist(check, provider, permissions)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onCheck, enabled = !check.isChecking) {
                    Text(stringResource(Res.string.action_check_again))
                }
                ExternalLinkButton(
                    stringResource(Res.string.action_create_token, provider),
                    account.providerId.tokenPageUrl,
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: StringResource,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
    }
}
