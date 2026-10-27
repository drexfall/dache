package dev.dache.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.action_cancel
import dev.dache.app.resources.link_dialog_body
import dev.dache.app.resources.link_dialog_dont_ask
import dev.dache.app.resources.link_dialog_open
import dev.dache.app.resources.link_dialog_title
import dev.dache.app.resources.link_opens_externally
import dev.dache.core.linking.normalizeHost
import org.jetbrains.compose.resources.stringResource

/** The only way UI code leaves the app. Asks first unless the user turned that off. */
fun interface LinkOpener {
    fun open(url: String)
}

val LocalLinkOpener = staticCompositionLocalOf<LinkOpener> { error("No LinkOpener provided") }

/**
 * Provides [LocalLinkOpener] to [content]. When [confirm] is on, links wait for the user in a dialog;
 * ticking "Don't ask again" and opening calls [onStopAsking].
 */
@Composable
fun ExternalLinkHost(
    confirm: Boolean,
    onStopAsking: () -> Unit,
    content: @Composable () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val currentConfirm by rememberUpdatedState(confirm)
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val opener =
        remember(uriHandler) {
            LinkOpener { url -> if (currentConfirm) pending = url else uriHandler.openUri(url) }
        }
    CompositionLocalProvider(LocalLinkOpener provides opener, content = content)

    pending?.let { url ->
        ConfirmLinkDialog(
            url = url,
            onOpen = { dontAskAgain ->
                pending = null
                if (dontAskAgain) onStopAsking()
                uriHandler.openUri(url)
            },
            onDismiss = { pending = null },
        )
    }
}

@Composable
private fun ConfirmLinkDialog(
    url: String,
    onOpen: (dontAskAgain: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var dontAskAgain by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(OpenInNewIcon, contentDescription = null) },
        title = { Text(stringResource(Res.string.link_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.link_dialog_body, normalizeHost(url) ?: url))
                // Show the full address so the user can see exactly where they are going.
                Text(url, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = dontAskAgain, role = Role.Checkbox, onValueChange = { dontAskAgain = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = dontAskAgain, onCheckedChange = null)
                    Text(stringResource(Res.string.link_dialog_dont_ask), modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onOpen(dontAskAgain) }) { Text(stringResource(Res.string.link_dialog_open)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** A text button that leaves the app, marked with the open-in-new icon. */
@Composable
fun ExternalLinkButton(
    text: String,
    url: String,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
) {
    val opener = LocalLinkOpener.current
    val content: @Composable () -> Unit = {
        Text(text)
        Icon(
            OpenInNewIcon,
            contentDescription = stringResource(Res.string.link_opens_externally),
            modifier = Modifier.padding(start = 6.dp).size(16.dp),
        )
    }
    if (outlined) {
        OutlinedButton(onClick = { opener.open(url) }, modifier = modifier) { content() }
    } else {
        TextButton(onClick = {
            opener.open(url)
        }, modifier = modifier, contentPadding = ButtonDefaults.TextButtonContentPadding) {
            content()
        }
    }
}

/** Icon-only variant for dense rows; [label] says what opens. */
@Composable
fun ExternalLinkIconButton(
    url: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val opener = LocalLinkOpener.current
    IconButton(onClick = { opener.open(url) }, modifier = modifier) {
        Icon(OpenInNewIcon, contentDescription = label, modifier = Modifier.size(18.dp))
    }
}
