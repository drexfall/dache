package dev.dache.core.boards

import dev.dache.core.model.ProviderIcon
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64

/**
 * What a board shows beside its name: one of the app's own [symbol]s, named so a symbol the app no longer has
 * falls back to the default, or an [image] the user uploaded, base64. Kept flat rather than as a sealed type so a
 * board saved by a newer version still loads. Make one with [ofSymbol] or [ofImage].
 */
@Serializable
data class BoardIcon(
    val symbol: String? = null,
    val image: String? = null,
) {
    /** The uploaded image, or null for a symbol or an image that no longer reads as one. */
    val uploaded: ProviderIcon? by lazy {
        image?.let { runCatching { Base64.decode(it) }.getOrNull() }?.let(ProviderIcon::of)
    }

    companion object {
        fun ofSymbol(symbol: String) = BoardIcon(symbol = symbol)

        /** [icon] as a board icon; it's already been checked for size and format by [ProviderIcon.of]. */
        fun ofImage(icon: ProviderIcon) = BoardIcon(image = Base64.encode(icon.bytes))

        /** The default board's: it stands in for the overview, the app's home. */
        const val HOME = "home"
    }
}
