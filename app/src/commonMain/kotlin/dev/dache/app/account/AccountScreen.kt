package dev.dache.app.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dache.app.dashboard.CollapsibleSection
import dev.dache.app.dashboard.ProviderErrorCard
import dev.dache.app.dashboard.StatTiles
import dev.dache.app.dashboard.activityCharts
import dev.dache.app.navigation.Destination
import dev.dache.app.resources.Res
import dev.dache.app.resources.account_services
import dev.dache.app.resources.action_account_settings
import dev.dache.app.resources.open_in
import dev.dache.app.resources.overview_activity
import dev.dache.app.resources.updated_at
import dev.dache.app.service.serviceCards
import dev.dache.app.ui.components.ExternalLinkButton
import dev.dache.app.ui.components.relativeTime
import dev.dache.app.ui.layout.AdaptiveGrid
import dev.dache.app.ui.layout.FormFactor
import dev.dache.app.ui.layout.LocalFormFactor
import dev.dache.app.ui.layout.PageScaffold
import dev.dache.core.dashboard.DashboardState
import dev.dache.core.model.ConnectedAccount
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/**
 * One provider account as its own dashboard: headline numbers, a card per page of its services, then charts.
 * The resources themselves live on those pages.
 */
@Composable
fun AccountScreen(
    account: ConnectedAccount,
    state: DashboardState,
    onRefresh: () -> Unit,
    onOpenMenu: (() -> Unit)?,
    onNavigate: (Destination) -> Unit,
) {
    val result = state.results.firstOrNull { it.account.id == account.id }
    val snapshot = result?.snapshot
    val compact = LocalFormFactor.current == FormFactor.COMPACT
    val openLabel = stringResource(Res.string.open_in, account.providerId.displayName)
    val dashboardUrl = account.providerId.dashboardUrl
    PageScaffold(
        title = account.providerId.displayName,
        subtitle =
            listOfNotNull(
                account.displayName,
                state.lastUpdated?.let { stringResource(Res.string.updated_at, relativeTime(it)) },
            ).joinToString(" · "),
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        onOpenMenu = onOpenMenu,
        // Desktop has room in the toolbar; on phones the button gets its own row so the title isn't squeezed.
        actions = {
            if (!compact) ExternalLinkButton(openLabel, dashboardUrl)
            IconButton(onClick = { onNavigate(Destination.AccountSettings(account.id)) }) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(Res.string.action_account_settings))
            }
        },
    ) {
        if (compact) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ExternalLinkButton(openLabel, dashboardUrl, outlined = true)
            }
        }
        if (result?.error != null) ProviderErrorCard(result, onRefresh)
        if (snapshot == null) {
            if (result?.error == null) {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            return@PageScaffold
        }
        val now = state.lastUpdated ?: Clock.System.now()
        StatTiles(accountStats(snapshot, now))
        val pages = state.pages(account.providerId)
        if (pages.isNotEmpty()) {
            val cards = serviceCards(pages, snapshot, now) { onNavigate(Destination.Service(account.id, it.id)) }
            CollapsibleSection(Res.string.account_services) {
                AdaptiveGrid(cards, minCellWidth = 240.dp, maxColumns = 4, spacing = 12.dp)
            }
        }
        val charts = activityCharts(listOf(snapshot), now)
        if (charts.isNotEmpty()) {
            CollapsibleSection(Res.string.overview_activity) { AdaptiveGrid(charts, minCellWidth = 420.dp) }
        }
    }
}
