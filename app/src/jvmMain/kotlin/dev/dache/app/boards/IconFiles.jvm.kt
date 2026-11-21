package dev.dache.app.boards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.IOException

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "ico", "svg", "bmp")

@Composable
actual fun rememberImageFilePicker(
    maxBytes: Int,
    onPicked: (ByteArray?) -> Unit,
): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    return remember(maxBytes) {
        {
            val dialog = FileDialog(null as Frame?, "Choose an icon", FileDialog.LOAD)
            dialog.setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in IMAGE_EXTENSIONS }
            dialog.isVisible = true
            val name = dialog.file
            if (name != null) {
                val file = File(dialog.directory, name)
                val bytes =
                    try {
                        file.takeIf { it.length() <= maxBytes }?.readBytes()
                    } catch (_: IOException) {
                        null
                    }
                callback.value(bytes)
            }
        }
    }
}

internal actual fun ImageBitmap.encodeToPng(): ByteArray? =
    Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
