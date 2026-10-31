package dev.dache.app.plugins

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.IOException

@Composable
actual fun rememberManifestFilePicker(
    maxBytes: Int,
    onPicked: (String?) -> Unit,
): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    return remember(maxBytes) {
        {
            val dialog = FileDialog(null as Frame?, "Choose a plugin manifest", FileDialog.LOAD)
            dialog.setFilenameFilter { _, name -> name.endsWith(".json") }
            dialog.isVisible = true
            val name = dialog.file
            if (name != null) {
                val file = File(dialog.directory, name)
                val text =
                    try {
                        file.takeIf { it.length() <= maxBytes }?.readText()
                    } catch (_: IOException) {
                        null
                    }
                callback.value(text)
            }
        }
    }
}
