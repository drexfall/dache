package dev.dache.app.profiles

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.action_cancel
import dev.dache.app.resources.profile_accounts
import dev.dache.app.resources.profile_create
import dev.dache.app.resources.profile_default
import dev.dache.app.resources.profile_delete
import dev.dache.app.resources.profile_delete_body
import dev.dache.app.resources.profile_delete_empty
import dev.dache.app.resources.profile_delete_title
import dev.dache.app.resources.profile_name_label
import dev.dache.app.resources.profile_new
import dev.dache.app.resources.profile_next
import dev.dache.app.resources.profile_open_menu
import dev.dache.app.resources.profile_rename
import dev.dache.app.resources.profile_save
import dev.dache.app.resources.profile_switcher_description
import dev.dache.core.model.Profile
import dev.dache.core.model.Profiles
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** What the profile switcher can do. Rename and delete act on the active profile. */
class ProfileActions(
    val onSwitch: (id: String) -> Unit,
    val onCycle: () -> Unit,
    val onCreate: (name: String) -> Unit,
    val onRename: (id: String, name: String) -> Unit,
    val onDelete: (id: String) -> Unit,
) {
    companion object {
        val None = ProfileActions({}, {}, {}, { _, _ -> }, {})
    }
}

/** The name to show for [profile]; the unnamed default profile gets a translated one. */
@Composable
fun profileName(profile: Profile): String = profile.name.ifBlank { stringResource(Res.string.profile_default) }

private enum class ProfileDialog { CREATE, RENAME, DELETE }

/**
 * The active profile, at the top of the sidebar. Tapping lists the profiles to switch to and lets the user add,
 * rename or delete one; double-tapping moves straight to the next profile, wrapping around. [accountCount] is how
 * many accounts the active profile has.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileSwitcher(
    profiles: Profiles,
    accountCount: Int,
    actions: ProfileActions,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<ProfileDialog?>(null) }
    val active = profiles.active
    val name = profileName(active)
    val description = stringResource(Res.string.profile_switcher_description, name)
    val nextLabel = stringResource(Res.string.profile_next)
    val menuLabel = stringResource(Res.string.profile_open_menu)
    val canCycle = profiles.all.size > 1

    Box(modifier) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .combinedClickable(
                        role = Role.Button,
                        onClickLabel = menuLabel,
                        onClick = { menuOpen = true },
                        onDoubleClick = if (canCycle) actions.onCycle else null,
                    ).semantics(mergeDescendants = true) {
                        contentDescription = description
                        // Screen readers use double-tap to activate, so cycling gets its own action.
                        if (canCycle) {
                            customActions =
                                listOf(
                                    CustomAccessibilityAction(nextLabel) {
                                        actions.onCycle()
                                        true
                                    },
                                )
                        }
                    },
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProfileBadge(name, size = 36.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        pluralStringResource(Res.plurals.profile_accounts, accountCount, accountCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            profiles.all.forEach { profile ->
                val label = profileName(profile)
                DropdownMenuItem(
                    text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { ProfileBadge(label, size = 24.dp) },
                    trailingIcon = {
                        if (profile.id == active.id) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                    onClick = {
                        menuOpen = false
                        actions.onSwitch(profile.id)
                    },
                )
            }
            HorizontalDivider()
            MenuAction(stringResource(Res.string.profile_new), Icons.Filled.Add) {
                menuOpen = false
                dialog = ProfileDialog.CREATE
            }
            MenuAction(stringResource(Res.string.profile_rename), Icons.Filled.Edit) {
                menuOpen = false
                dialog = ProfileDialog.RENAME
            }
            // The last profile stays, so there is always somewhere for accounts to live.
            MenuAction(stringResource(Res.string.profile_delete), Icons.Filled.Delete, enabled = canCycle) {
                menuOpen = false
                dialog = ProfileDialog.DELETE
            }
        }
    }

    when (dialog) {
        ProfileDialog.CREATE ->
            ProfileNameDialog(
                title = stringResource(Res.string.profile_new),
                initial = "",
                confirmLabel = stringResource(Res.string.profile_create),
                onConfirm = actions.onCreate,
                onDismiss = { dialog = null },
            )
        ProfileDialog.RENAME ->
            ProfileNameDialog(
                title = stringResource(Res.string.profile_rename),
                initial = active.name,
                confirmLabel = stringResource(Res.string.profile_save),
                onConfirm = { actions.onRename(active.id, it) },
                onDismiss = { dialog = null },
            )
        ProfileDialog.DELETE ->
            DeleteProfileDialog(
                name = name,
                accountCount = accountCount,
                onConfirm = { actions.onDelete(active.id) },
                onDismiss = { dialog = null },
            )
        null -> Unit
    }
}

@Composable
private fun MenuAction(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun ProfileNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    val submit = {
        onConfirm(name)
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.profile_name_label)) },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = submit, enabled = name.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@Composable
private fun DeleteProfileDialog(
    name: String,
    accountCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.profile_delete_title, name)) },
        text = {
            Text(
                if (accountCount == 0) {
                    stringResource(Res.string.profile_delete_empty)
                } else {
                    pluralStringResource(Res.plurals.profile_delete_body, accountCount, accountCount)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(stringResource(Res.string.profile_delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** A lettered circle for a profile, like the provider avatars beside accounts. */
@Composable
fun ProfileBadge(
    name: String,
    size: Dp = 28.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(size).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
