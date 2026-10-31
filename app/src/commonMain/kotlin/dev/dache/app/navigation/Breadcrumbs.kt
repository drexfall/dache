package dev.dache.app.navigation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.breadcrumb_current
import dev.dache.app.resources.breadcrumb_settings
import dev.dache.app.resources.nav_manage_accounts
import dev.dache.app.resources.nav_overview
import dev.dache.app.service.pageTitle
import dev.dache.core.dashboard.DashboardState
import dev.dache.core.model.ProviderId
import org.jetbrains.compose.resources.stringResource

/**
 * The pages that led here, oldest first; tapping one goes back to it. Scrolls sideways when it doesn't fit and
 * starts scrolled to the end, so the current page is always visible.
 */
@Composable
fun Breadcrumbs(
    trail: List<Destination>,
    state: DashboardState,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState(), reverseScrolling = true)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        trail.forEachIndexed { index, destination ->
            if (index > 0) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Crumb(destination, state, current = index == trail.lastIndex) { onSelect(destination) }
        }
    }
}

@Composable
private fun Crumb(
    destination: Destination,
    state: DashboardState,
    current: Boolean,
    onClick: () -> Unit,
) {
    val label = crumbLabel(destination, state)
    val currentText = stringResource(Res.string.breadcrumb_current)
    TextButton(
        onClick = onClick,
        enabled = !current,
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = if (current) Modifier.semantics { stateDescription = currentText } else Modifier,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            crumbProvider(destination, state)?.let { ProviderAvatar(it, Modifier.size(20.dp)) }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp),
            )
        }
    }
}

@Composable
private fun crumbLabel(
    destination: Destination,
    state: DashboardState,
): String =
    when (destination) {
        Destination.Overview -> stringResource(Res.string.nav_overview)
        Destination.ManageAccounts -> stringResource(Res.string.nav_manage_accounts)
        is Destination.Account ->
            state.accounts
                .firstOrNull { it.id == destination.accountId }
                ?.providerId
                ?.displayName
                ?: destination.accountId
        is Destination.Resource -> state.resource(destination.ref)?.displayName ?: destination.ref.nativeId
        is Destination.Service ->
            state.accounts
                .firstOrNull { it.id == destination.accountId }
                ?.let { state.page(it.providerId, destination.pageId) }
                ?.let { pageTitle(it) }
                ?: destination.pageId
        is Destination.AccountSettings -> stringResource(Res.string.breadcrumb_settings)
    }

private fun crumbProvider(
    destination: Destination,
    state: DashboardState,
): ProviderId? =
    when (destination) {
        is Destination.Account -> state.accounts.firstOrNull { it.id == destination.accountId }?.providerId
        is Destination.Resource -> destination.ref.providerId
        else -> null
    }
