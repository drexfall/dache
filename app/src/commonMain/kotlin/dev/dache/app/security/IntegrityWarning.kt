package dev.dache.app.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dache.app.resources.Res
import dev.dache.app.resources.action_continue
import dev.dache.app.resources.action_dont_show_again
import dev.dache.app.resources.integrity_body
import dev.dache.app.resources.integrity_bootloader_unlocked
import dev.dache.app.resources.integrity_note_body
import dev.dache.app.resources.integrity_note_title
import dev.dache.app.resources.integrity_root_manager
import dev.dache.app.resources.integrity_su_binary
import dev.dache.app.resources.integrity_test_keys
import dev.dache.app.resources.integrity_title
import dev.dache.app.resources.integrity_unverified_boot
import dev.dache.core.security.IntegrityChecker
import dev.dache.core.security.IntegritySignal
import dev.dache.core.security.IntegrityWarningSettings
import dev.dache.core.settings.SettingsStore
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Runs [checker] once; empty until it answers, and always empty without one. */
@Composable
fun rememberIntegritySignals(checker: IntegrityChecker?): Set<IntegritySignal> {
    val signals by produceState(emptySet<IntegritySignal>(), checker) { value = checker?.check().orEmpty() }
    return signals
}

/**
 * The advisory shown once there is an account whose token is at stake, and again if [signals] change. It never
 * blocks: both buttons carry on into the app.
 */
@Composable
fun IntegrityWarning(
    signals: Set<IntegritySignal>,
    hasAccounts: Boolean,
    settingsStore: SettingsStore,
    onSave: (IntegrityWarningSettings) -> Unit,
) {
    val settings by settingsStore.integrityWarning.collectAsStateWithLifecycle(initialValue = null)
    if (settings?.shouldWarn(signals, hasAccounts) != true) return
    AlertDialog(
        onDismissRequest = { onSave(IntegrityWarningSettings(seen = signals)) },
        icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
        title = { Text(stringResource(Res.string.integrity_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.integrity_body))
                SignalList(signals)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(IntegrityWarningSettings(seen = signals)) }) {
                Text(stringResource(Res.string.action_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = { onSave(IntegrityWarningSettings(seen = signals, neverShow = true)) }) {
                Text(stringResource(Res.string.action_dont_show_again))
            }
        },
    )
}

/** The quiet note in settings while [signals] hold, whatever was chosen in the warning. */
@Composable
fun IntegrityNote(signals: Set<IntegritySignal>) {
    if (signals.isEmpty()) return
    Card {
        ListItem(
            leadingContent = { Icon(Icons.Filled.Warning, contentDescription = null) },
            headlineContent = { Text(stringResource(Res.string.integrity_note_title)) },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(Res.string.integrity_note_body))
                    SignalList(signals)
                }
            },
        )
    }
}

@Composable
private fun SignalList(signals: Set<IntegritySignal>) {
    Column {
        signals.sorted().forEach {
            Text("• ${stringResource(it.label())}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun IntegritySignal.label(): StringResource =
    when (this) {
        IntegritySignal.SU_BINARY -> Res.string.integrity_su_binary
        IntegritySignal.TEST_KEYS -> Res.string.integrity_test_keys
        IntegritySignal.ROOT_MANAGER -> Res.string.integrity_root_manager
        IntegritySignal.BOOTLOADER_UNLOCKED -> Res.string.integrity_bootloader_unlocked
        IntegritySignal.UNVERIFIED_BOOT -> Res.string.integrity_unverified_boot
    }
