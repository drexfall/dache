package dev.dache.app.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dache.app.navigation.LocalAppNavigator
import dev.dache.app.navigation.ProviderAvatar
import dev.dache.app.resources.Res
import dev.dache.app.resources.detail_linked
import dev.dache.app.resources.link_open_resource
import dev.dache.app.resources.open_in
import dev.dache.app.ui.components.ExternalLinkButton
import dev.dache.core.dashboard.DashboardState
import dev.dache.core.model.Resource
import org.jetbrains.compose.resources.stringResource

/** Linked resources as chips that open the resource's page inside the app (forward arrow), unlike external links. */
@Composable
fun LinkedResources(
    resource: Resource,
    state: DashboardState,
) {
    // Collapse repeats (every past run of a workflow links to the same site) to one chip per named resource.
    val linked =
        state
            .linkedTo(resource.ref)
            .mapNotNull { state.resource(it) }
            .distinctBy { Triple(it.providerId, it.accountId, it.displayName) }
    if (linked.isEmpty()) return
    val navigator = LocalAppNavigator.current
    Text(
        stringResource(Res.string.detail_linked),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        linked.forEach { other ->
            val label = "${other.providerId.displayName} · ${other.displayName}"
            AssistChip(
                onClick = { navigator.openResource(other.ref) },
                label = { Text(label) },
                leadingIcon = { ProviderAvatar(other.providerId, Modifier.size(20.dp)) },
                trailingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(Res.string.link_open_resource, label),
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
    }
}

@Composable
fun OpenInProviderButton(resource: Resource) {
    ExternalLinkButton(
        text = stringResource(Res.string.open_in, resource.providerId.displayName),
        url = resource.webUrl,
        outlined = true,
    )
}
