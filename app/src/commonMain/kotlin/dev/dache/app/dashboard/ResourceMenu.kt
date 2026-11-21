package dev.dache.app.dashboard

import androidx.compose.runtime.Composable
import dev.dache.app.actions.LocalActionHost
import dev.dache.app.resources.Res
import dev.dache.app.resources.action_collapse
import dev.dache.app.resources.action_expand
import dev.dache.app.resources.context_copy_address
import dev.dache.app.resources.context_copy_link
import dev.dache.app.resources.context_copy_name
import dev.dache.app.resources.context_visit
import dev.dache.app.resources.open_in
import dev.dache.app.ui.components.ContextMenuItem
import dev.dache.app.ui.components.LocalLinkOpener
import dev.dache.app.ui.components.OpenInNewIcon
import dev.dache.app.ui.components.rememberTextCopier
import dev.dache.core.linking.normalizeHost
import dev.dache.core.model.ActionTarget
import dev.dache.core.model.Monitor
import dev.dache.core.model.Resource
import dev.dache.core.model.Site
import org.jetbrains.compose.resources.stringResource

/**
 * What a right click on [resource]'s row offers: showing or hiding its details, opening it in the provider or at
 * its own address, copying those, and the provider's actions on it. Actions on a site's deployments stay in the
 * expanded row, which says which deployment each one is for.
 */
@Composable
internal fun resourceMenuItems(
    resource: Resource,
    expanded: Boolean,
    onToggle: () -> Unit,
): List<ContextMenuItem> {
    val opener = LocalLinkOpener.current
    val copier = rememberTextCopier()
    val provider = resource.providerId.displayName
    val address = resource.address()
    val host = address?.let { normalizeHost(it) ?: it }
    val actionHost = LocalActionHost.current
    val actions = actionHost?.actionsFor(resource, onDeployments = false).orEmpty()
    return buildList {
        add(
            ContextMenuItem(stringResource(if (expanded) Res.string.action_collapse else Res.string.action_expand)) {
                onToggle()
            },
        )
        add(
            ContextMenuItem(stringResource(Res.string.open_in, provider), icon = OpenInNewIcon, startsGroup = true) {
                opener.open(resource.webUrl)
            },
        )
        if (address != null && host != null) {
            add(
                ContextMenuItem(stringResource(Res.string.context_visit, host), icon = OpenInNewIcon) {
                    opener.open(address)
                },
            )
        }
        add(
            ContextMenuItem(stringResource(Res.string.context_copy_link, provider), startsGroup = true) {
                copier.copy(resource.webUrl)
            },
        )
        if (address != null) {
            add(ContextMenuItem(stringResource(Res.string.context_copy_address)) { copier.copy(address) })
        }
        add(ContextMenuItem(stringResource(Res.string.context_copy_name)) { copier.copy(resource.displayName) })
        actions.forEachIndexed { index, action ->
            add(
                ContextMenuItem(action.label, destructive = action.destructive, startsGroup = index == 0) {
                    actionHost?.start(resource, action, ActionTarget(resource.ref), resource.displayName)
                },
            )
        }
    }
}

/** Where the resource itself lives, for those that have an address of their own. */
private fun Resource.address(): String? =
    when (this) {
        is Site -> primaryUrl
        is Monitor -> url
        else -> null
    }.takeIf { !it.isNullOrBlank() }
