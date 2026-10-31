package dev.dache.app.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.actions_deployment
import dev.dache.app.resources.actions_heading
import dev.dache.app.ui.components.relativeTime
import dev.dache.core.model.ActionInfo
import dev.dache.core.model.ActionTarget
import dev.dache.core.model.Resource
import dev.dache.core.model.Site
import org.jetbrains.compose.resources.stringResource

/** What resource views need to offer actions: the ones that apply, and a way to start one. */
interface ActionHost {
    fun actionsFor(
        resource: Resource,
        onDeployments: Boolean,
    ): List<ActionInfo>

    fun start(
        resource: Resource,
        action: ActionInfo,
        target: ActionTarget,
        targetName: String,
    )
}

/** Null where actions aren't offered, such as screenshots and widgets. */
val LocalActionHost = staticCompositionLocalOf<ActionHost?> { null }

/**
 * The resource's actions as buttons, and for a site, the actions on each of its recent deployments. Shows nothing
 * when the provider offers none.
 */
@Composable
fun ResourceActions(resource: Resource) {
    val host = LocalActionHost.current ?: return
    val own = host.actionsFor(resource, onDeployments = false)
    val deployments =
        (resource as? Site)
            ?.deployments
            ?.sortedByDescending { it.createdAt }
            ?.take(RECENT)
            .orEmpty()
    val onDeploy = if (deployments.isEmpty()) emptyList() else host.actionsFor(resource, onDeployments = true)
    if (own.isEmpty() && onDeploy.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.actions_heading), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            own.forEach { action ->
                ActionButton(action) {
                    host.start(resource, action, ActionTarget(resource.ref), resource.displayName)
                }
            }
        }
        deployments.forEach { deploy ->
            if (onDeploy.isNotEmpty()) {
                val name = deploy.commitMessage ?: deploy.branch ?: deploy.nativeId
                Text(
                    stringResource(Res.string.actions_deployment, name, relativeTime(deploy.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onDeploy.forEach { action ->
                        ActionButton(action) {
                            host.start(resource, action, ActionTarget(resource.ref, deploy.nativeId), name)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    action: ActionInfo,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick) {
        Text(
            action.label,
            color = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

private const val RECENT = 3
