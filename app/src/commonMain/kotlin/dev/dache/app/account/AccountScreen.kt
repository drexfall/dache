package dev.dache.app.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dache.app.dashboard.CollapsibleSection
import dev.dache.app.dashboard.ProviderErrorCard
import dev.dache.app.dashboard.StatTiles
import dev.dache.app.dashboard.activityCharts
import dev.dache.app.dashboard.resourceSections
import dev.dache.app.resources.Res
import dev.dache.app.resources.open_in
import dev.dache.app.resources.overview_activity
import dev.dache.app.resources.overview_resources
import dev.dache.app.resources.updated_at
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

/** One provider account as its own dashboard: headline numbers, charts, then its resources. */
@Composable
fun AccountScreen(
    account: ConnectedAccount,
    state: DashboardState,
    onRefresh: () -> Unit,
    onOpenMenu: (() -> Unit)?,
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
        val charts = activityCharts(listOf(snapshot), now)
        if (charts.isNotEmpty()) {
            CollapsibleSection(Res.string.overview_activity) { AdaptiveGrid(charts, minCellWidth = 420.dp) }
        }
        val sections = resourceSections(listOf(snapshot), state)
        if (sections.isNotEmpty()) {
            CollapsibleSection(Res.string.overview_resources) { AdaptiveGrid(sections, minCellWidth = 420.dp) }
        }
    }
}
