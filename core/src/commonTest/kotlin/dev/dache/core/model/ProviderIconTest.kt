package dev.dache.core.model

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProviderIconTest {
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10)

    @Test
    fun formatComesFromTheBytesNotTheLabel() {
        assertEquals(
            IconFormat.PNG,
            ProviderIcon.fromDataUri("data:image/svg+xml;base64,${Base64.encode(png)}")?.format,
        )
        assertEquals(IconFormat.ICO, ProviderIcon.of(byteArrayOf(0, 0, 1, 0, 1, 0))?.format)
        assertEquals(IconFormat.JPEG, ProviderIcon.of(byteArrayOf(-1, -40, -1, -32))?.format)
        assertEquals(
            IconFormat.WEBP,
            ProviderIcon.of("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".encodeToByteArray())?.format,
        )
        assertEquals(IconFormat.SVG, ProviderIcon.of("\n<?xml version=\"1.0\"?><svg/>".encodeToByteArray())?.format)
        assertNull(ProviderIcon.of("<!doctype html><html></html>".encodeToByteArray()))
        assertNull(ProviderIcon.of(ByteArray(0)))
        assertNull(ProviderIcon.of(png + ByteArray(IconFormat.MAX_BYTES)))
    }

    @Test
    fun readsBase64AndPercentEncodedDataUris() {
        assertEquals(ProviderIcon.of(png), ProviderIcon.fromDataUri("DATA:image/png;base64,${Base64.encode(png)}"))
        assertEquals(
            IconFormat.SVG,
            ProviderIcon.fromDataUri("data:image/svg+xml,%3Csvg%20xmlns='http://www.w3.org/2000/svg'/%3E")?.format,
        )
        assertNull(ProviderIcon.fromDataUri("data:text/plain;base64,${Base64.encode(png)}"))
        assertNull(ProviderIcon.fromDataUri("data:image/png;base64,***"))
        assertNull(ProviderIcon.fromDataUri("https://example.com/icon.png"))
    }
}
