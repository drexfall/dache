package dev.dache.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Material "open in new" (Apache 2.0). Not in material-icons-core, and the extended set is a large
 * dependency for one glyph, so the path is inlined. Marks every action that leaves the app.
 */
val OpenInNewIcon: ImageVector by lazy {
    ImageVector
        .Builder(
            name = "OpenInNew",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData =
                addPathNodes(
                    "M19 19H5V5h7V3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2v-7h-2v7z" +
                        "M14 3v2h3.59l-9.83 9.83 1.41 1.41L19 6.41V10h2V3h-7z",
                ),
            fill = SolidColor(Color.Black),
        ).build()
}
