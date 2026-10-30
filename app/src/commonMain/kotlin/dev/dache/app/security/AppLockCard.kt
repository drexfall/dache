package dev.dache.app.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.lock_timeout_five_minutes
import dev.dache.app.resources.lock_timeout_immediately
import dev.dache.app.resources.lock_timeout_one_minute
import dev.dache.app.resources.pref_app_lock
import dev.dache.app.resources.pref_app_lock_body
import dev.dache.app.resources.pref_app_lock_unavailable
import dev.dache.app.resources.pref_hide_widget_details
import dev.dache.app.resources.pref_hide_widget_details_body
import dev.dache.app.resources.pref_lock_timeout
import dev.dache.core.settings.AppLockSettings
import dev.dache.core.settings.LockTimeout
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * App lock preferences. [available] is false when the device has no screen lock: the lock can't be turned on
 * then, though one left on can still be turned off.
 */
@Composable
fun AppLockCard(
    settings: AppLockSettings,
    available: Boolean,
    onToggleLock: () -> Unit,
    onChange: (AppLockSettings) -> Unit,
) {
    Card {
        Column {
            ListItem(
                modifier =
                    Modifier.toggleable(
                        value = settings.enabled,
                        enabled = available || settings.enabled,
                        role = Role.Switch,
                        onValueChange = { onToggleLock() },
                    ),
                headlineContent = { Text(stringResource(Res.string.pref_app_lock)) },
                supportingContent = {
                    Text(
                        stringResource(
                            if (available) Res.string.pref_app_lock_body else Res.string.pref_app_lock_unavailable,
                        ),
                    )
                },
                leadingContent = { Icon(Icons.Filled.Lock, contentDescription = null) },
                trailingContent = {
                    Switch(checked = settings.enabled, onCheckedChange = null, enabled = available || settings.enabled)
                },
            )
            if (settings.enabled) {
                // A ListItem like its neighbours, so the chips sit on the same background.
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.pref_lock_timeout)) },
                    supportingContent = {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LockTimeout.entries.forEach { timeout ->
                                FilterChip(
                                    selected = timeout == settings.timeout,
                                    onClick = { onChange(settings.copy(timeout = timeout)) },
                                    label = { Text(stringResource(timeout.label())) },
                                )
                            }
                        }
                    },
                )
                ListItem(
                    modifier =
                        Modifier.toggleable(
                            value = settings.hideWidgetDetails,
                            role = Role.Switch,
                            onValueChange = { onChange(settings.copy(hideWidgetDetails = it)) },
                        ),
                    headlineContent = { Text(stringResource(Res.string.pref_hide_widget_details)) },
                    supportingContent = { Text(stringResource(Res.string.pref_hide_widget_details_body)) },
                    trailingContent = { Switch(checked = settings.hideWidgetDetails, onCheckedChange = null) },
                )
            }
        }
    }
}

private fun LockTimeout.label(): StringResource =
    when (this) {
        LockTimeout.IMMEDIATELY -> Res.string.lock_timeout_immediately
        LockTimeout.ONE_MINUTE -> Res.string.lock_timeout_one_minute
        LockTimeout.FIVE_MINUTES -> Res.string.lock_timeout_five_minutes
    }
