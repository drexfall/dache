package dev.dache.app.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import dev.dache.app.navigation.ProviderAvatar
import dev.dache.app.resources.Res
import dev.dache.app.resources.accounts_empty_body
import dev.dache.app.resources.accounts_empty_title
import dev.dache.app.resources.accounts_title
import dev.dache.app.resources.action_add_account
import dev.dache.app.resources.action_cancel
import dev.dache.app.resources.action_remove
import dev.dache.app.resources.pref_confirm_links
import dev.dache.app.resources.pref_confirm_links_body
import dev.dache.app.resources.remove_account_body
import dev.dache.app.resources.remove_account_description
import dev.dache.app.resources.remove_account_title
import dev.dache.app.ui.components.EmptyState
import dev.dache.app.ui.components.OpenInNewIcon
import dev.dache.app.ui.layout.FormFactor
import dev.dache.app.ui.layout.LocalFormFactor
import dev.dache.app.ui.layout.PageScaffold
import dev.dache.core.model.ConnectedAccount
import org.jetbrains.compose.resources.stringResource

/**
 * Lists connected accounts with remove; tapping one opens its settings. Adding goes through the shared
 * add-account flow.
 */
@Composable
fun AccountsScreen(
    accounts: List<ConnectedAccount>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenMenu: (() -> Unit)?,
    onAddAccount: () -> Unit,
    onRemove: (ConnectedAccount) -> Unit,
    onOpenSettings: (ConnectedAccount) -> Unit,
    confirmExternalLinks: Boolean,
    onConfirmExternalLinksChange: (Boolean) -> Unit,
    /** Platform security preferences, e.g. the app lock where the device has a screen lock. */
    securityCard: (@Composable () -> Unit)? = null,
) {
    var pendingRemoval by remember { mutableStateOf<ConnectedAccount?>(null) }
    val compact = LocalFormFactor.current == FormFactor.COMPACT

    PageScaffold(
        title = stringResource(Res.string.accounts_title),
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        onOpenMenu = onOpenMenu,
        // Phones get a thumb-reachable FAB; desktop keeps the action in the toolbar.
        actions = {
            if (!compact) TextButton(onClick = onAddAccount) { Text(stringResource(Res.string.action_add_account)) }
        },
        floatingActionButton = {
            if (compact) {
                ExtendedFloatingActionButton(
                    onClick = onAddAccount,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(Res.string.action_add_account)) },
                )
            }
        },
    ) {
        PreferencesCard(confirmExternalLinks, onConfirmExternalLinksChange)
        securityCard?.invoke()
        if (accounts.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.AccountCircle,
                title = stringResource(Res.string.accounts_empty_title),
                body = stringResource(Res.string.accounts_empty_body),
            )
            return@PageScaffold
        }
        Card {
            Column {
                accounts.forEach { account ->
                    ListItem(
                        modifier = Modifier.clickable(role = Role.Button) { onOpenSettings(account) },
                        leadingContent = { ProviderAvatar(account.providerId) },
                        headlineContent = { Text(account.displayName) },
                        supportingContent = { Text(account.providerId.displayName) },
                        trailingContent = {
                            IconButton(onClick = { pendingRemoval = account }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription =
                                        stringResource(
                                            Res.string.remove_account_description,
                                            account.displayName,
                                        ),
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    pendingRemoval?.let { account ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(Res.string.remove_account_title, account.displayName)) },
            text = { Text(stringResource(Res.string.remove_account_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(account)
                    pendingRemoval = null
                }) { Text(stringResource(Res.string.action_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }
}

/** Lets the user turn the "leave the app?" prompt back on after choosing "Don't ask again". */
@Composable
private fun PreferencesCard(
    confirmExternalLinks: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Card {
        ListItem(
            modifier = Modifier.toggleable(value = confirmExternalLinks, role = Role.Switch, onValueChange = onChange),
            headlineContent = { Text(stringResource(Res.string.pref_confirm_links)) },
            supportingContent = { Text(stringResource(Res.string.pref_confirm_links_body)) },
            leadingContent = { Icon(OpenInNewIcon, contentDescription = null) },
            trailingContent = { Switch(checked = confirmExternalLinks, onCheckedChange = null) },
        )
    }
}
