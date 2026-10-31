package dev.dache.app.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import dev.dache.app.resources.permission_checking
import dev.dache.app.resources.permission_granted
import dev.dache.app.resources.permission_missing
import dev.dache.app.resources.permission_needed
import dev.dache.app.resources.permission_optional
import dev.dache.app.resources.permission_required
import dev.dache.app.resources.permission_unchecked
import dev.dache.app.resources.token_checking
import dev.dache.app.resources.token_missing_required
import dev.dache.app.resources.token_needs
import dev.dache.app.resources.token_no_permissions
import dev.dache.app.resources.token_refused
import dev.dache.app.resources.token_valid
import dev.dache.app.ui.components.errorMessage
import dev.dache.app.ui.theme.LocalStatusColors
import dev.dache.core.model.PermissionCheck
import dev.dache.core.model.TokenCheck
import dev.dache.core.model.TokenPermission
import org.jetbrains.compose.resources.stringResource

/** Where a permission's row is: listed before there is a token, being checked, or checked. */
private enum class Stage { NEEDED, CHECKING, CHECKED }

/**
 * Whether the token works and, one line each, which of [permissions] it has. Before a token is entered the rows
 * say what it needs; while it is checked each shows a spinner; afterwards each is granted, missing or unchecked. A
 * missing required permission is red; a missing optional one is only flagged, since it just leaves its page empty.
 */
@Composable
fun TokenChecklist(
    state: TokenCheckState,
    providerName: String,
    permissions: List<TokenPermission>,
    modifier: Modifier = Modifier,
) {
    val result = state.result.takeUnless { state.isChecking }
    val stage =
        when {
            state.isChecking -> Stage.CHECKING
            result != null -> Stage.CHECKED
            else -> Stage.NEEDED
        }
    val rows = result?.permissions ?: permissions.map { PermissionCheck(it, granted = null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Headline(state, result, providerName, hasRows = rows.isNotEmpty())
        rows.forEach { PermissionLine(it, stage) }
        if (result?.accepted == true && result.permissions.isEmpty()) {
            Note(stringResource(Res.string.token_no_permissions, providerName))
        }
        if (result?.accepted == true && result.missingRequired) {
            Note(stringResource(Res.string.token_missing_required), MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun Headline(
    state: TokenCheckState,
    result: TokenCheck?,
    providerName: String,
    hasRows: Boolean,
) {
    val error = state.error
    val colors = LocalStatusColors.current
    when {
        state.isChecking ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(Res.string.token_checking), style = MaterialTheme.typography.bodyMedium)
            }
        error != null -> CheckLine(Icons.Filled.Close, MaterialTheme.colorScheme.error, errorMessage(error), null)
        result?.displayName != null ->
            CheckLine(
                Icons.Filled.CheckCircle,
                colors.success,
                stringResource(Res.string.token_valid, result.displayName.orEmpty()),
                null,
            )
        result != null ->
            CheckLine(
                Icons.Filled.Close,
                MaterialTheme.colorScheme.error,
                stringResource(Res.string.token_refused, providerName),
                null,
            )
        hasRows -> Text(stringResource(Res.string.token_needs), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun PermissionLine(
    check: PermissionCheck,
    stage: Stage,
) {
    val permission = check.permission
    val need =
        stringResource(if (permission.required) Res.string.permission_required else Res.string.permission_optional)
    val status =
        when {
            stage == Stage.NEEDED -> Res.string.permission_needed
            stage == Stage.CHECKING -> Res.string.permission_checking
            check.granted == true -> Res.string.permission_granted
            check.granted == false -> Res.string.permission_missing
            else -> Res.string.permission_unchecked
        }
    val description = stringResource(status, permission.name)
    if (stage == Stage.CHECKING) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.clearAndSetSemantics { contentDescription = "$description. $need" },
        ) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            }
            Lines(permission.name, need)
        }
    } else {
        val (icon, tint) = icon(check, stage)
        CheckLine(icon, tint, permission.name, need, description)
    }
}

@Composable
private fun icon(
    check: PermissionCheck,
    stage: Stage,
): Pair<ImageVector, Color> {
    val colors = LocalStatusColors.current
    return when {
        stage == Stage.NEEDED -> Icons.Filled.Info to MaterialTheme.colorScheme.onSurfaceVariant
        check.granted == true -> Icons.Filled.CheckCircle to colors.success
        check.granted == false && check.permission.required -> Icons.Filled.Close to MaterialTheme.colorScheme.error
        check.granted == false -> Icons.Filled.Close to colors.building
        else -> Icons.Filled.Info to colors.unknown
    }
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
        Lines(text, caption)
    }
}

@Composable
private fun Lines(
    text: String,
    caption: String?,
) {
    Column {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        caption?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Note(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}
