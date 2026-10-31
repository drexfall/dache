package dev.dache.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.app_name
import dev.dache.app.resources.nav_accounts_header
import dev.dache.app.resources.nav_add_account
import dev.dache.app.resources.nav_manage_accounts
import dev.dache.app.resources.nav_no_accounts
import dev.dache.app.resources.nav_overview
import dev.dache.app.resources.nav_plugins
import dev.dache.app.resources.nav_problems
import dev.dache.app.resources.nav_refresh_failed
import dev.dache.app.service.pageTitle
import dev.dache.app.service.problemsOn
import dev.dache.core.dashboard.DashboardState
import dev.dache.core.dashboard.DashboardSummary
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.ServicePage
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/** Overview, then one entry per connected account, then account management. */
@Composable
fun Sidebar(
    state: DashboardState,
    current: Destination,
    onSelect: (Destination) -> Unit,
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        Text(
            stringResource(Res.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 16.dp, bottom = 16.dp),
        )
        val totalProblems = state.summary.problemCount
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.nav_overview)) },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            selected = current == Destination.Overview,
            onClick = { onSelect(Destination.Overview) },
            badge = { ProblemBadge(totalProblems) },
        )

        Text(
            stringResource(Res.string.nav_accounts_header),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
        )
        if (state.accounts.isEmpty()) {
            Text(
                stringResource(Res.string.nav_no_accounts),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
        }
        val now = Clock.System.now()
        state.accounts.forEach { account ->
            val result = state.results.firstOrNull { it.account.id == account.id }
            val problems = result?.snapshot?.let { DashboardSummary.from(listOf(it), now).problemCount } ?: 0
            NavigationDrawerItem(
                label = {
                    Column {
                        Text(account.providerId.displayName, maxLines = 1)
                        Text(
                            account.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                icon = { ProviderAvatar(account.providerId) },
                selected = current == Destination.Account(account.id),
                onClick = { onSelect(Destination.Account(account.id)) },
                badge = {
                    if (result?.error != null) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = stringResource(Res.string.nav_refresh_failed),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        ProblemBadge(problems)
                    }
                },
            )
            // The open account's pages, so moving between them is one tap.
            if (current.accountId == account.id) {
                ServicePageItems(account.id, state.pages(account.providerId), result?.snapshot, current, onSelect)
            }
        }
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.nav_add_account)) },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            selected = false,
            onClick = onAddAccount,
        )
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.nav_manage_accounts)) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            selected = current == Destination.ManageAccounts,
            onClick = { onSelect(Destination.ManageAccounts) },
        )
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.nav_plugins)) },
            icon = { Icon(Icons.Filled.Build, contentDescription = null) },
            selected = current == Destination.Plugins,
            onClick = { onSelect(Destination.Plugins) },
        )
    }
}

@Composable
private fun ServicePageItems(
    accountId: String,
    pages: List<ServicePage>,
    snapshot: ProviderSnapshot?,
    current: Destination,
    onSelect: (Destination) -> Unit,
) {
    val now = Clock.System.now()
    pages.forEach { page ->
        val destination = Destination.Service(accountId, page.id)
        NavigationDrawerItem(
            label = { Text(pageTitle(page), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            selected = current == destination,
            onClick = { onSelect(destination) },
            badge = { ProblemBadge(snapshot?.problemsOn(page, now) ?: 0) },
            modifier = Modifier.padding(start = 40.dp),
        )
    }
}

@Composable
private fun ProblemBadge(count: Int) {
    if (count <= 0) return
    val description = stringResource(Res.string.nav_problems, count)
    Badge(modifier = Modifier.padding(start = 4.dp)) {
        Text(count.toString(), modifier = Modifier.clearAndSetSemantics { contentDescription = description })
    }
}

/** A lettered circle per provider. Brand logos are deliberately not used. */
@Composable
fun ProviderAvatar(
    provider: ProviderId,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(28.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            provider.displayName.take(1),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
