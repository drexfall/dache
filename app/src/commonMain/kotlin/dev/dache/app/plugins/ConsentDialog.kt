package dev.dache.app.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.action_cancel
import dev.dache.app.resources.action_install
import dev.dache.app.resources.action_update
import dev.dache.app.resources.consent_added
import dev.dache.app.resources.consent_asks_for
import dev.dache.app.resources.consent_auth
import dev.dache.app.resources.consent_auth_was
import dev.dache.app.resources.consent_hosts
import dev.dache.app.resources.consent_install_title
import dev.dache.app.resources.consent_no_publisher
import dev.dache.app.resources.consent_publisher
import dev.dache.app.resources.consent_removed
import dev.dache.app.resources.consent_shows
import dev.dache.app.resources.consent_token
import dev.dache.app.resources.consent_trust_unverified
import dev.dache.app.resources.consent_unverified
import dev.dache.app.resources.consent_unverified_body
import dev.dache.app.resources.consent_update_title
import dev.dache.app.resources.consent_verified
import dev.dache.app.resources.consent_verified_body
import dev.dache.app.ui.theme.LocalStatusColors
import dev.dache.plugin.registry.PendingInstall
import dev.dache.plugin.registry.describe
import org.jetbrains.compose.resources.stringResource

/**
 * Shown before a plugin is installed, and before an update that changes the hosts it calls or where the token
 * goes: which hosts it contacts, how it sends the token, what it asks for and shows, who published it, and
 * whether the registry verified it. An unverified plugin needs an extra tick before it can be installed.
 */
@Composable
fun ConsentDialog(
    pending: PendingInstall,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val manifest = pending.manifest
    val update = pending.current != null
    var trusted by remember(pending) { mutableStateOf(pending.verified) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (update) {
                    stringResource(Res.string.consent_update_title, manifest.name, manifest.version)
                } else {
                    stringResource(Res.string.consent_install_title, manifest.name, manifest.version)
                },
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TrustBanner(pending.verified)
                Section(stringResource(Res.string.consent_publisher)) {
                    Text(manifest.publisher ?: stringResource(Res.string.consent_no_publisher))
                }
                Section(stringResource(Res.string.consent_hosts)) { HostList(pending) }
                Section(stringResource(Res.string.consent_auth)) {
                    Text(manifest.auth.describe(), fontFamily = FontFamily.Monospace)
                    pending.current?.manifest?.auth?.takeIf { it != manifest.auth }?.let { before ->
                        Text(
                            stringResource(Res.string.consent_auth_was, before.describe()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Section(stringResource(Res.string.consent_asks_for)) {
                    val asks = listOf(stringResource(Res.string.consent_token)) + manifest.settings.map { it.label }
                    Text(asks.joinToString(", "))
                }
                Section(stringResource(Res.string.consent_shows)) {
                    Text(manifest.capabilities.joinToString(", ") { capabilityLabel(it) })
                }
                if (!pending.verified) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(value = trusted, role = Role.Checkbox, onValueChange = { trusted = it }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = trusted, onCheckedChange = null)
                        Text(
                            stringResource(Res.string.consent_trust_unverified),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = trusted) {
                Text(stringResource(if (update) Res.string.action_update else Res.string.action_install))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@Composable
private fun TrustBanner(verified: Boolean) {
    val status = LocalStatusColors.current
    val (icon, tint) =
        if (verified) {
            Icons.Filled.CheckCircle to status.success
        } else {
            Icons.Filled.Warning to
                status.failed
        }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Column {
                Text(
                    stringResource(if (verified) Res.string.consent_verified else Res.string.consent_unverified),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(
                        if (verified) Res.string.consent_verified_body else Res.string.consent_unverified_body,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Every host it will call, with what an update adds marked, and what it drops listed after. */
@Composable
private fun HostList(pending: PendingInstall) {
    val before =
        pending.current
            ?.manifest
            ?.hosts
            .orEmpty()
            .toSet()
    val after = pending.manifest.hosts
    after.forEach { host ->
        val added = pending.current != null && host !in before
        Text(
            if (added) stringResource(Res.string.consent_added, host) else host,
            fontFamily = FontFamily.Monospace,
            color = if (added) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (added) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
    (before - after.toSet()).forEach { host ->
        Text(
            stringResource(Res.string.consent_removed, host),
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

/** `DNS` → `DNS`, `SITES` → `Sites`: capability names as words. */
internal fun capabilityLabel(name: String): String =
    when (name) {
        "DNS", "CI" -> name
        else -> name.lowercase().replaceFirstChar { it.uppercase() }
    }
