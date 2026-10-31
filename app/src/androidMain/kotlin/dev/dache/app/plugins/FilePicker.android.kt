package dev.dache.app.plugins

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import java.io.IOException

@Composable
actual fun rememberManifestFilePicker(
    maxBytes: Int,
    onPicked: (String?) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onPicked)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val text =
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        // One byte over the limit is enough to know it's too large.
                        input.readNBytesCompat(maxBytes + 1).takeIf { it.size <= maxBytes }?.decodeToString()
                    }
                } catch (_: IOException) {
                    null
                } catch (_: SecurityException) {
                    null
                }
            callback.value(text)
        }
    return { launcher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
}

/** InputStream.readNBytes needs API 33. */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(BUFFER_SIZE)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

private const val BUFFER_SIZE = 8192
