package dev.dache.app.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.permission_granted
import dev.dache.app.resources.permission_missing
import dev.dache.app.resources.permission_optional
import dev.dache.app.resources.permission_required
import dev.dache.app.resources.permission_unchecked
import dev.dache.app.resources.token_checking
import dev.dache.app.resources.token_missing_required
import dev.dache.app.resources.token_no_permissions
import dev.dache.app.resources.token_valid
import dev.dache.app.ui.components.errorMessage
import dev.dache.app.ui.theme.LocalStatusColors
import dev.dache.core.model.PermissionCheck
import org.jetbrains.compose.resources.stringResource

/**
 * Whether the token works and, one line each, which permissions it has. A missing required permission is red;
 * a missing optional one is only flagged, since it just leaves its own page empty. Shows nothing before a check.
 */
@Composable
fun TokenChecklist(
    state: TokenCheckState,
    providerName: String,
    modifier: Modifier = Modifier,
) {
    val result = state.result
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            state.isChecking ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(Res.string.token_checking), style = MaterialTheme.typography.bodyMedium)
                }
            state.error != null ->
                CheckLine(Icons.Filled.Close, MaterialTheme.colorScheme.error, errorMessage(state.error), null)
            result != null -> {
                CheckLine(
                    Icons.Filled.CheckCircle,
                    LocalStatusColors.current.success,
                    stringResource(Res.string.token_valid, result.displayName),
                    null,
                )
                result.permissions.forEach { PermissionLine(it) }
                if (result.permissions.isEmpty()) {
                    Text(
                        stringResource(Res.string.token_no_permissions, providerName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (result.missingRequired) {
                    Text(
                        stringResource(Res.string.token_missing_required),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionLine(check: PermissionCheck) {
    val permission = check.permission
    val colors = LocalStatusColors.current
    val (icon, tint) =
        when (check.granted) {
            true -> Icons.Filled.CheckCircle to colors.success
            false -> Icons.Filled.Close to if (permission.required) MaterialTheme.colorScheme.error else colors.building
            null -> Icons.Filled.Info to colors.unknown
        }
    val status =
        when (check.granted) {
            true -> Res.string.permission_granted
            false -> Res.string.permission_missing
            null -> Res.string.permission_unchecked
        }
    val need =
        stringResource(if (permission.required) Res.string.permission_required else Res.string.permission_optional)
    CheckLine(icon, tint, permission.name, need, stringResource(status, permission.name))
}

/** [description] replaces the visible text for screen readers, so the status isn't conveyed by the icon alone. */
@Composable
private fun CheckLine(
    icon: ImageVector,
    tint: Color,
    text: String,
    caption: String?,
    description: String = text,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier.clearAndSetSemantics {
                contentDescription = listOfNotNull(description, caption).joinToString(". ")
            },
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Column {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            caption?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
