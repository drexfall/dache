package dev.dache.app.navigation

import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density

// Compose has no SVG decoder on Android. Favicons are picked raster-first and manifest icons must be raster, so
// this only leaves a letter for a site whose sole icon is an SVG.
internal actual fun decodeSvg(
    bytes: ByteArray,
    density: Density,
): Painter? = null
