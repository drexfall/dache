package dev.dache.app.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.dache.app.navigation.ProviderAvatar
import dev.dache.app.resources.Res
import dev.dache.app.resources.action_cancel
import dev.dache.app.resources.action_check_now
import dev.dache.app.resources.action_choose_file
import dev.dache.app.resources.action_install
import dev.dache.app.resources.action_install_link
import dev.dache.app.resources.action_remove
import dev.dache.app.resources.action_update
import dev.dache.app.resources.plugin_file_unreadable
import dev.dache.app.resources.plugin_link_label
import dev.dache.app.resources.plugin_remove_body_bundled
import dev.dache.app.resources.plugin_remove_body_only
import dev.dache.app.resources.plugin_remove_description
import dev.dache.app.resources.plugin_remove_title
import dev.dache.app.resources.plugin_source_bundled
import dev.dache.app.resources.plugin_source_registry
import dev.dache.app.resources.plugin_source_sideloaded
import dev.dache.app.resources.plugin_unverified
import dev.dache.app.resources.plugin_update_reviews
import dev.dache.app.resources.plugin_verified
import dev.dache.app.resources.plugins_available
import dev.dache.app.resources.plugins_installed
import dev.dache.app.resources.plugins_none_available
import dev.dache.app.resources.plugins_registry
import dev.dache.app.resources.plugins_registry_checked
import dev.dache.app.resources.plugins_registry_checking
import dev.dache.app.resources.plugins_registry_unchecked
import dev.dache.app.resources.plugins_sideload
import dev.dache.app.resources.plugins_sideload_body
import dev.dache.app.resources.plugins_title
import dev.dache.app.resources.plugins_updates
import dev.dache.app.ui.components.Paged
import dev.dache.app.ui.layout.PageScaffold
import dev.dache.app.ui.theme.LocalStatusColors
import dev.dache.plugin.api.IndexEntry
import dev.dache.plugin.registry.Plugin
import dev.dache.plugin.registry.PluginSource
import dev.dache.plugin.registry.RegistryClient
import dev.dache.plugin.registry.RegistryStatus
import dev.dache.plugin.registry.Update
import dev.dache.plugin.runtime.providerId
import org.jetbrains.compose.resources.stringResource

/**
 * Plugins in use, updates and new plugins from the registry, and, behind an opt-in, installing from a link or a
 * file. Installs open [ConsentDialog] first.
 */
@Composable
fun PluginsScreen(
    viewModel: PluginsViewModel,
    state: PluginsUiState,
    onOpenMenu: (() -> Unit)?,
) {
    var removing by remember { mutableStateOf<Plugin?>(null) }
    PageScaffold(
        title = stringResource(Res.string.plugins_title),
        isRefreshing = state.registry == RegistryStatus.Checking,
        onRefresh = viewModel::checkRegistry,
        onOpenMenu = onOpenMenu,
    ) {
        RegistryCard(state.registry, onCheck = viewModel::checkRegistry)
        if (state.work.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.work.error?.let { ErrorText(it.message ?: it.toString()) }
        if (state.updates.isNotEmpty()) {
            SectionCard(stringResource(Res.string.plugins_updates)) {
                Paged(
                    state.updates,
                    key = "plugin-updates",
                ) { UpdateRow(it, onUpdate = { viewModel.install(it.entry) }) }
            }
        }
        SectionCard(stringResource(Res.string.plugins_installed)) {
            Paged(state.plugins, key = "plugins-installed") { plugin ->
                InstalledRow(plugin, onRemove = {
                    removing =
                        plugin
                })
            }
        }
        if (state.registry is RegistryStatus.Ready) {
            SectionCard(stringResource(Res.string.plugins_available)) {
                if (state.available.isEmpty()) {
                    Text(
                        stringResource(Res.string.plugins_none_available),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Paged(
                    state.available,
                    key = "plugins-available",
                ) { AvailableRow(it, onInstall = { viewModel.install(it) }) }
            }
        }
        SideloadCard(state.allowSideload, state.work.busy, viewModel)
    }

    state.work.pending?.let { ConsentDialog(it, onConfirm = viewModel::confirm, onDismiss = viewModel::dismiss) }
    removing?.let { plugin ->
        val bundled = plugin.id in viewModel.bundledIds
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(Res.string.plugin_remove_title, plugin.manifest.name)) },
            text = {
                Text(
                    stringResource(
                        if (bundled) Res.string.plugin_remove_body_bundled else Res.string.plugin_remove_body_only,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(plugin)
                    removing = null
                }) { Text(stringResource(Res.string.action_remove)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { removing = null },
                ) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun RegistryCard(
    status: RegistryStatus,
    onCheck: () -> Unit,
) {
    Card {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(stringResource(Res.string.plugins_registry)) },
            supportingContent = {
                when (status) {
                    RegistryStatus.Unchecked -> Text(stringResource(Res.string.plugins_registry_unchecked))
                    RegistryStatus.Checking -> Text(stringResource(Res.string.plugins_registry_checking))
                    is RegistryStatus.Ready ->
                        Text(stringResource(Res.string.plugins_registry_checked, status.index.plugins.size))
                    is RegistryStatus.Failed -> ErrorText(status.error.message ?: status.error.toString())
                }
            },
            trailingContent = {
                TextButton(onClick = onCheck, enabled = status != RegistryStatus.Checking) {
                    Text(stringResource(Res.string.action_check_now))
                }
            },
        )
    }
}

@Composable
private fun InstalledRow(
    plugin: Plugin,
    onRemove: () -> Unit,
) {
    val source =
        when (plugin.source) {
            PluginSource.BUNDLED -> Res.string.plugin_source_bundled
            PluginSource.REGISTRY -> Res.string.plugin_source_registry
            PluginSource.SIDELOADED -> Res.string.plugin_source_sideloaded
        }
    val manifest = plugin.manifest
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { ProviderAvatar(manifest.providerId()) },
        headlineContent = { Text(manifest.name) },
        supportingContent = {
            Column {
                Text(
                    listOfNotNull(
                        "v${manifest.version}",
                        manifest.publisher,
                        stringResource(source),
                    ).joinToString(" · "),
                )
                VerifiedLabel(plugin.verified)
            }
        },
        trailingContent = {
            if (plugin.source != PluginSource.BUNDLED) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(Res.string.plugin_remove_description, manifest.name),
                    )
                }
            }
        },
    )
}

@Composable
private fun VerifiedLabel(verified: Boolean) {
    val status = LocalStatusColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (verified) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (verified) status.success else status.failed,
            modifier = Modifier.size(14.dp),
        )
        Text(
            stringResource(if (verified) Res.string.plugin_verified else Res.string.plugin_unverified),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun UpdateRow(
    update: Update,
    onUpdate: () -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(update.entry.name) },
        supportingContent = {
            Column {
                Text("v${update.current.manifest.version} → v${update.entry.version}")
                if (update.needsApproval) {
                    Text(stringResource(Res.string.plugin_update_reviews), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        trailingContent = { OutlinedButton(onClick = onUpdate) { Text(stringResource(Res.string.action_update)) } },
    )
}

@Composable
private fun AvailableRow(
    entry: IndexEntry,
    onInstall: () -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(entry.name) },
        supportingContent = {
            Text(
                listOfNotNull(
                    "v${entry.version}",
                    entry.publisher,
                    entry.capabilities.joinToString { capabilityLabel(it) },
                ).joinToString(" · "),
            )
        },
        trailingContent = { OutlinedButton(onClick = onInstall) { Text(stringResource(Res.string.action_install)) } },
    )
}

@Composable
private fun SideloadCard(
    allowed: Boolean,
    busy: Boolean,
    viewModel: PluginsViewModel,
) {
    var link by rememberSaveable { mutableStateOf("") }
    var unreadable by remember { mutableStateOf(false) }
    val pickFile =
        rememberManifestFilePicker(RegistryClient.MAX_MANIFEST_BYTES) { text ->
            unreadable = text == null
            text?.let(viewModel::installFromFile)
        }
    Card {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier =
                Modifier.toggleable(
                    value = allowed,
                    role = Role.Switch,
                    onValueChange = viewModel::setAllowSideload,
                ),
            headlineContent = { Text(stringResource(Res.string.plugins_sideload)) },
            supportingContent = { Text(stringResource(Res.string.plugins_sideload_body)) },
            trailingContent = { Switch(checked = allowed, onCheckedChange = null) },
        )
        if (allowed) {
            HorizontalDivider()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text(stringResource(Res.string.plugin_link_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { viewModel.installFromLink(link) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.installFromLink(link) },
                        enabled = !busy && link.isNotBlank(),
                    ) {
                        Text(stringResource(Res.string.action_install_link))
                    }
                    OutlinedButton(onClick = pickFile, enabled = !busy) {
                        Text(stringResource(Res.string.action_choose_file))
                    }
                }
                if (unreadable) ErrorText(stringResource(Res.string.plugin_file_unreadable))
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp))
        Card { Column { content() } }
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}
