package dev.dache.app.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.dache.core.icons.ProviderIcons
import dev.dache.core.model.IconFormat
import dev.dache.core.model.ProviderIcon
import dev.dache.core.model.ProviderId
import org.jetbrains.compose.resources.decodeToImageBitmap

/** The icons [ProviderAvatar] draws; empty means every provider shows its letter. */
val LocalProviderIcons = compositionLocalOf { ProviderIcons() }

/**
 * A provider's icon in a circle: the plugin's own, else its site's favicon, else the first letter of its name.
 * Pass [accountId] where an account is shown, so a self-hosted account gets its own server's icon. Icons sit on a
 * white disc in either theme, because most are drawn for a light browser tab and vanish on a dark one.
 */
@Composable
fun ProviderAvatar(
    provider: ProviderId,
    modifier: Modifier = Modifier,
    accountId: String? = null,
) {
    val icon = LocalProviderIcons.current.of(provider, accountId)
    val density = LocalDensity.current
    val painter = remember(icon, density) { icon?.let { iconPainter(it, density) } }
    if (painter != null) {
        Box(modifier.size(28.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Image(
                painter,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(ICON_INSET),
            )
        }
    } else {
        Box(
            modifier.size(28.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                provider.displayName.take(1),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

private val ICON_INSET = 4.dp

/** An SVG icon as a painter, or null where the platform can't draw SVG (Android); the letter shows then. */
internal expect fun decodeSvg(
    bytes: ByteArray,
    density: Density,
): Painter?

/**
 * [icon] as a painter, for provider and board icons alike. Null when the bytes don't decode on this platform, so
 * a fallback is drawn instead. Decoders throw whatever their platform throws on a corrupt image; any of it means
 * "use the fallback".
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal fun iconPainter(
    icon: ProviderIcon,
    density: Density,
): Painter? =
    try {
        when (icon.format) {
            IconFormat.SVG -> decodeSvg(icon.bytes, density)
            else -> BitmapPainter(icon.bytes.decodeToImageBitmap())
        }
    } catch (e: Exception) {
        null
    }
