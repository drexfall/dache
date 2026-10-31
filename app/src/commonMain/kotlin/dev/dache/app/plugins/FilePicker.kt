package dev.dache.app.plugins

import androidx.compose.runtime.Composable

/**
 * Returns a function that asks the user for a manifest file and passes its text to [onPicked], or null when the
 * file can't be read or is larger than [maxBytes]. Nothing is called when the user cancels.
 */
@Composable
expect fun rememberManifestFilePicker(
    maxBytes: Int,
    onPicked: (String?) -> Unit,
): () -> Unit
