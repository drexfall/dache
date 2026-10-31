package dev.dache.app.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.action_open_notification_settings
import dev.dache.app.resources.alerts_body
import dev.dache.app.resources.alerts_ci
import dev.dache.app.resources.alerts_denied
import dev.dache.app.resources.alerts_deploys
import dev.dache.app.resources.alerts_every
import dev.dache.app.resources.alerts_interval_hours
import dev.dache.app.resources.alerts_interval_minutes
import dev.dache.app.resources.alerts_monitors
import dev.dache.app.resources.alerts_title
import dev.dache.core.alerts.AlertSettings
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Whether the app may post notifications, and how to ask. */
interface NotificationAccess {
    fun isGranted(): Boolean

    /** Asks if needed; true once notifications are allowed. */
    suspend fun request(): Boolean

    /** The system page where the user turns Dache's notifications back on. */
    fun openSettings()
}

/** How a platform runs background checks: [access] to notify, and an optional [footer] about scheduling. */
class BackgroundChecksPlatform(
    val access: NotificationAccess,
    val footer: (@Composable () -> Unit)? = null,
)

/**
 * Turns background checks on and off, sets how often they run and what they alert on. Turning them on asks for
 * permission to notify first. [footer] is for platform details, such as how the checks are scheduled.
 */
@Composable
fun BackgroundChecksCard(
    settings: AlertSettings,
    access: NotificationAccess,
    onChange: (AlertSettings) -> Unit,
    footer: (@Composable () -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var denied by remember { mutableStateOf(false) }
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
    Card {
        ListItem(
            modifier =
                Modifier.toggleable(value = settings.enabled, role = Role.Switch) { on ->
                    if (!on) {
                        onChange(settings.copy(enabled = false))
                    } else {
                        scope.launch {
                            denied = !access.request()
                            if (!denied) onChange(settings.copy(enabled = true))
                        }
                    }
                },
            colors = transparent,
            leadingContent = { Icon(Icons.Filled.Notifications, contentDescription = null) },
            headlineContent = { Text(stringResource(Res.string.alerts_title)) },
            supportingContent = { Text(stringResource(Res.string.alerts_body)) },
            trailingContent = { Switch(checked = settings.enabled, onCheckedChange = null) },
        )
        if (denied) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    stringResource(Res.string.alerts_denied),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = access::openSettings) {
                    Text(stringResource(Res.string.action_open_notification_settings))
                }
            }
        }
        if (settings.enabled) {
            HorizontalDivider()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.alerts_every), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlertSettings.INTERVALS.forEach { minutes ->
                        FilterChip(
                            selected = settings.intervalMinutes == minutes,
                            onClick = { onChange(settings.copy(intervalMinutes = minutes)) },
                            label = { Text(intervalLabel(minutes)) },
                        )
                    }
                }
                KindRow(Res.string.alerts_deploys, settings.deploys) { onChange(settings.copy(deploys = it)) }
                KindRow(Res.string.alerts_monitors, settings.monitors) { onChange(settings.copy(monitors = it)) }
                KindRow(Res.string.alerts_ci, settings.ci) { onChange(settings.copy(ci = it)) }
                footer?.invoke()
            }
        }
    }
}

@Composable
private fun intervalLabel(minutes: Int): String =
    if (minutes % MINUTES_PER_HOUR == 0) {
        stringResource(Res.string.alerts_interval_hours, minutes / MINUTES_PER_HOUR)
    } else {
        stringResource(Res.string.alerts_interval_minutes, minutes)
    }

@Composable
private fun KindRow(
    label: StringResource,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
    }
}

private const val MINUTES_PER_HOUR = 60
