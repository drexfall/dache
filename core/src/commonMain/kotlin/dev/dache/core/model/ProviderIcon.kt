package dev.dache.core.model

import kotlin.io.encoding.Base64

/**
 * The image formats a provider icon may be in, told apart by their first bytes rather than by what a server or
 * manifest claims. Anything else is not used as an icon.
 */
enum class IconFormat(
    val mimeType: String,
) {
    PNG("image/png"),
    JPEG("image/jpeg"),
    GIF("image/gif"),
    WEBP("image/webp"),
    ICO("image/x-icon"),
    SVG("image/svg+xml"),
    ;

    companion object {
        /** The largest icon the app keeps, from a manifest or a provider's site. Favicons are a few KB. */
        const val MAX_BYTES = 64 * 1024

        private const val SNIFF_CHARS = 256
        private const val WEBP_TAG_AT = 8

        private val pngMagic = byteArrayOf(0x89.toByte()) + "PNG".encodeToByteArray()
        private val jpegMagic = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        private val gifMagic = "GIF8".encodeToByteArray()
        private val riffMagic = "RIFF".encodeToByteArray()
        private val webpTag = "WEBP".encodeToByteArray()
        private val icoMagic = byteArrayOf(0, 0, 1, 0)

        fun sniff(bytes: ByteArray): IconFormat? =
            when {
                bytes.has(pngMagic) -> PNG
                bytes.has(jpegMagic) -> JPEG
                bytes.has(gifMagic) -> GIF
                bytes.has(riffMagic) && bytes.has(webpTag, at = WEBP_TAG_AT) -> WEBP
                bytes.has(icoMagic) -> ICO
                isSvg(bytes) -> SVG
                else -> null
            }

        private fun isSvg(bytes: ByteArray): Boolean {
            val head =
                bytes
                    .copyOfRange(0, minOf(bytes.size, SNIFF_CHARS))
                    .decodeToString()
                    .trimStart('\uFEFF', ' ', '\t', '\r', '\n')
            val opens = head.startsWith("<svg") || head.startsWith("<?xml") || head.startsWith("<!")
            return opens && "<svg" in bytes.decodeToString()
        }

        private fun ByteArray.has(
            magic: ByteArray,
            at: Int = 0,
        ): Boolean = size >= at + magic.size && magic.indices.all { this[at + it] == magic[it] }
    }
}

/** An icon's bytes and their sniffed format. Two icons are equal when their bytes are. */
class ProviderIcon private constructor(
    val bytes: ByteArray,
    val format: IconFormat,
) {
    override fun equals(other: Any?): Boolean = other is ProviderIcon && other.bytes.contentEquals(bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    companion object {
        /** [bytes] as an icon, or null when they're empty, too large, or not an image format the app reads. */
        fun of(bytes: ByteArray): ProviderIcon? {
            if (bytes.isEmpty() || bytes.size > IconFormat.MAX_BYTES) return null
            return IconFormat.sniff(bytes)?.let { ProviderIcon(bytes, it) }
        }

        /**
         * A `data:` URI's image, e.g. `data:image/png;base64,iVBOR…` or `data:image/svg+xml,<svg …>`. Null when it
         * isn't a data URI, doesn't decode, or holds something other than a supported image.
         */
        fun fromDataUri(uri: String): ProviderIcon? {
            val comma = uri.indexOf(',')
            val meta =
                uri
                    .takeIf { comma > 0 && it.startsWith(DATA_PREFIX, ignoreCase = true) }
                    ?.substring(DATA_PREFIX.length, comma)
                    ?.split(';')
                    ?.map { it.trim().lowercase() }
                    ?.takeIf { it.first().startsWith("image/") }
            val payload = uri.substring(comma + 1)
            val bytes =
                when {
                    meta == null -> null
                    "base64" in meta.drop(1) ->
                        runCatching { Base64.decode(payload.filterNot { it.isWhitespace() }) }.getOrNull()
                    else -> percentDecode(payload)
                }
            return bytes?.let(::of)
        }

        private const val DATA_PREFIX = "data:"
        private const val HEX = 16
        private const val ESCAPE_LENGTH = 3

        private fun percentDecode(text: String): ByteArray? {
            val out = ArrayList<Byte>(text.length)
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '%') {
                    val end = i + ESCAPE_LENGTH
                    val code = text.substring(i + 1, minOf(text.length, end)).toIntOrNull(HEX) ?: return null
                    out += code.toByte()
                    i = end
                } else {
                    c.toString().encodeToByteArray().forEach { out += it }
                    i++
                }
            }
            return out.toByteArray()
        }
    }
}
