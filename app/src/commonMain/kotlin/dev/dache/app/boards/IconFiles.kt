package dev.dache.app.boards

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.dache.core.model.IconFormat
import dev.dache.core.model.ProviderIcon
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Returns a function that asks the user for an image and passes its bytes to [onPicked], or null when the file
 * can't be read or is larger than [maxBytes]. Nothing is called when the user cancels.
 */
@Composable
expect fun rememberImageFilePicker(
    maxBytes: Int,
    onPicked: (ByteArray?) -> Unit,
): () -> Unit

/** [this] encoded as a PNG, or null where it can't be. */
internal expect fun ImageBitmap.encodeToPng(): ByteArray?

/** The largest file offered as an icon; it's scaled down to [ICON_SIDE] before it's kept. */
const val MAX_ICON_FILE_BYTES = 8 * 1024 * 1024

/** Uploaded icons are kept at most this many pixels on a side: twice the largest size they're drawn at. */
private const val ICON_SIDE = 128

/**
 * An uploaded image as a board icon: SVGs as they are, if small enough, and anything else decoded and scaled to
 * fit [ICON_SIDE], so a photo straight off a phone fits the store's icon limit. Null when it isn't an image the
 * app can read.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
fun iconFromUpload(bytes: ByteArray): ProviderIcon? {
    if (IconFormat.sniff(bytes) == IconFormat.SVG) return ProviderIcon.of(bytes)
    val image =
        try {
            bytes.decodeToImageBitmap()
        } catch (e: Exception) {
            null
        }
    return image?.scaledToFit(ICON_SIDE)?.encodeToPng()?.let(ProviderIcon::of)
}

private fun ImageBitmap.scaledToFit(side: Int): ImageBitmap {
    val largest = max(width, height)
    if (largest <= side) return this
    val scale = side.toFloat() / largest
    val size = IntSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
    val scaled = ImageBitmap(size.width, size.height)
    Canvas(scaled).drawImageRect(
        this,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(width, height),
        dstOffset = IntOffset.Zero,
        dstSize = size,
        paint = Paint().apply { filterQuality = FilterQuality.High },
    )
    return scaled
}
