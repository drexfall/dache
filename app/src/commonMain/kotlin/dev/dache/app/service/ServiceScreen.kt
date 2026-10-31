package dev.dache.app.service

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dache.app.account.accountStats
import dev.dache.app.dashboard.CollapsibleSection
import dev.dache.app.dashboard.ProviderErrorCard
import dev.dache.app.dashboard.StatTiles
import dev.dache.app.dashboard.activityCharts
import dev.dache.app.dashboard.resourceSections
import dev.dache.app.navigation.Breadcrumbs
import dev.dache.app.navigation.Destination
import dev.dache.app.navigation.trail
import dev.dache.app.resources.Res
import dev.dache.app.resources.action_account_settings
import dev.dache.app.resources.overview_activity
import dev.dache.app.resources.overview_resources
import dev.dache.app.resources.service_empty
import dev.dache.app.resources.service_missing
import dev.dache.app.resources.service_missing_permissions
import dev.dache.app.ui.layout.AdaptiveGrid
import dev.dache.app.ui.layout.PageScaffold
import dev.dache.core.dashboard.DashboardState
import dev.dache.core.model.ConnectedAccount
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/** One page of an account, such as DNS: the account dashboard narrowed to what the page shows. */
@Composable
fun ServiceScreen(
    destination: Destination.Service,
    account: ConnectedAccount,
    state: DashboardState,
    onRefresh: () -> Unit,
    onOpenMenu: (() -> Unit)?,
    onNavigate: (Destination) -> Unit,
) {
    val page = state.page(account.providerId, destination.pageId)
    val result = state.results.firstOrNull { it.account.id == account.id }
    PageScaffold(
        title = page?.let { pageTitle(it) } ?: destination.pageId,
        subtitle = listOf(account.providerId.displayName, account.displayName).joinToString(" · "),
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        onOpenMenu = onOpenMenu,
        header = { Breadcrumbs(destination.trail, state, onNavigate) },
    ) {
        if (page == null) {
            Text(
                stringResource(Res.string.service_missing, account.providerId.displayName),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@PageScaffold
        }
        if (result?.error != null) ProviderErrorCard(result, onRefresh)
        val snapshot = result?.snapshot
        if (snapshot == null) {
            if (result?.error == null) {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            return@PageScaffold
        }
        val shown = snapshot.only(page)
        if (shown.missingPermissions.isNotEmpty()) {
            MissingPermissions(shown.missingPermissions) { onNavigate(Destination.AccountSettings(account.id)) }
        }
        val now = state.lastUpdated ?: Clock.System.now()
        val stats = accountStats(shown, now)
        if (stats.isNotEmpty()) StatTiles(stats)
        val charts = activityCharts(listOf(shown), now)
        if (charts.isNotEmpty()) {
            CollapsibleSection(Res.string.overview_activity) { AdaptiveGrid(charts, minCellWidth = 420.dp) }
        }
        val sections = resourceSections(listOf(shown), state)
        if (sections.isNotEmpty()) {
            CollapsibleSection(Res.string.overview_resources) { AdaptiveGrid(sections, minCellWidth = 420.dp) }
        } else if (shown.missingPermissions.isEmpty()) {
            Text(stringResource(Res.string.service_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Some of the page was left out because the token can't read it; the settings page shows the full check. */
@Composable
private fun MissingPermissions(
    permissions: Set<String>,
    onOpenSettings: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(Res.string.service_missing_permissions, permissions.sorted().joinToString(", ")),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onOpenSettings) { Text(stringResource(Res.string.action_account_settings)) }
        }
    }
}
