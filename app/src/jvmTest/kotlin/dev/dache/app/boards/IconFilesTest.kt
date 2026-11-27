package dev.dache.app.boards

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import dev.dache.core.model.IconFormat
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.skia.Bitmap
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IconFilesTest {
    @Test
    fun aLargeImageIsScaledDownToAnIcon() {
        // Noise doesn't compress, so the PNG is far over the icon size limit before scaling.
        val photo = noise(1200, 600)
        val bytes = assertNotNull(photo.encodeToPng())
        assertTrue(bytes.size > IconFormat.MAX_BYTES)

        val icon = assertNotNull(iconFromUpload(bytes))

        assertEquals(IconFormat.PNG, icon.format)
        val scaled = icon.bytes.decodeToImageBitmap()
        assertEquals(128 to 64, scaled.width to scaled.height, "fits 128 on its longest side, aspect kept")
    }

    @Test
    fun aSmallImageKeepsItsSize() {
        val icon = assertNotNull(iconFromUpload(assertNotNull(noise(32, 32).encodeToPng())))
        assertEquals(32, icon.bytes.decodeToImageBitmap().width)
    }

    @Test
    fun anSvgIsKeptAsItIs() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1 1"/>""".encodeToByteArray()
        assertEquals(IconFormat.SVG, iconFromUpload(svg)?.format)
    }

    @Test
    fun somethingThatIsntAnImageIsRefused() {
        assertNull(iconFromUpload("not an image".encodeToByteArray()))
        assertNull(iconFromUpload(ByteArray(0)))
    }

    /** Random opaque pixels, which PNG can't compress. */
    private fun noise(
        width: Int,
        height: Int,
    ): ImageBitmap {
        val bitmap = Bitmap()
        bitmap.allocN32Pixels(width, height)
        val pixels = Random(1).nextBytes(width * height * 4)
        for (i in 3 until pixels.size step 4) pixels[i] = 0xFF.toByte()
        bitmap.installPixels(pixels)
        return bitmap.asComposeImageBitmap()
    }
}
