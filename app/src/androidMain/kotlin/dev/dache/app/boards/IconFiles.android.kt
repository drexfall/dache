package dev.dache.app.boards

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import dev.dache.app.plugins.readNBytesCompat
import java.io.ByteArrayOutputStream
import java.io.IOException

@Composable
actual fun rememberImageFilePicker(
    maxBytes: Int,
    onPicked: (ByteArray?) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onPicked)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val bytes =
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        // One byte over the limit is enough to know it's too large.
                        input.readNBytesCompat(maxBytes + 1).takeIf { it.size <= maxBytes }
                    }
                } catch (_: IOException) {
                    null
                } catch (_: SecurityException) {
                    null
                }
            callback.value(bytes)
        }
    return { launcher.launch(arrayOf("image/*")) }
}

private const val PNG_QUALITY = 100

internal actual fun ImageBitmap.encodeToPng(): ByteArray? {
    val out = ByteArrayOutputStream()
    return if (asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)) out.toByteArray() else null
}
