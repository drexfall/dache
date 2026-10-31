package dev.dache.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Dynamic color where the platform supports it (Android 12+), otherwise null. */
@Composable
expect fun platformColorScheme(dark: Boolean): ColorScheme?

/** Status colors are fixed (not dynamic) so a red chip always means the same thing. */
data class StatusColors(
    val success: Color,
    val building: Color,
    val failed: Color,
    val cancelled: Color,
    val unknown: Color,
)

private val LightStatus =
    StatusColors(
        success = Color(0xFF1B7F3B),
        building = Color(0xFF1D5FBF),
        failed = Color(0xFFB3261E),
        cancelled = Color(0xFF5F6368),
        unknown = Color(0xFF7A6A00),
    )

private val DarkStatus =
    StatusColors(
        success = Color(0xFF7BD88F),
        building = Color(0xFF8AB4F8),
        failed = Color(0xFFF2B8B5),
        cancelled = Color(0xFFBDC1C6),
        unknown = Color(0xFFE6CF5C),
    )

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

/**
 * Chart chrome and marks. Values come from the validated dataviz reference palette; charts render on [surface]
 * rather than the dynamic theme surface so the contrast checks hold. Volume charts put failures in the fixed
 * critical status color on top of [accent] (categorical slot 1; deutan ΔE 25.7 dark, protan 23.8 light), which
 * single-series charts use too. [quiet] is a recessive step for marks that only matter as the backdrop to a
 * critical one, such as an uptime day without downtime.
 */
data class ChartColors(
    val surface: Color,
    val border: Color,
    val grid: Color,
    val baseline: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val mutedText: Color,
    val accent: Color,
    val critical: Color,
    val quiet: Color,
)

private val LightChart =
    ChartColors(
        surface = Color(0xFFFCFCFB),
        border = Color(0x1A0B0B0B),
        grid = Color(0xFFE1E0D9),
        baseline = Color(0xFFC3C2B7),
        primaryText = Color(0xFF0B0B0B),
        secondaryText = Color(0xFF52514E),
        mutedText = Color(0xFF898781),
        accent = Color(0xFF2A78D6),
        critical = Color(0xFFD03B3B),
        quiet = Color(0xFFD3D2CB),
    )

private val DarkChart =
    ChartColors(
        surface = Color(0xFF1A1A19),
        border = Color(0x1AFFFFFF),
        grid = Color(0xFF2C2C2A),
        baseline = Color(0xFF383835),
        primaryText = Color(0xFFF0EFEC),
        secondaryText = Color(0xFFC3C2B7),
        mutedText = Color(0xFF898781),
        accent = Color(0xFF3987E5),
        critical = Color(0xFFD03B3B),
        quiet = Color(0xFF3A3A37),
    )

val LocalChartColors = staticCompositionLocalOf { LightChart }

// Neutral warm grays from the same reference palette as the charts, so chart cards sit on the page instead of
// against Material's default lavender tint.
private val LightScheme =
    lightColorScheme(
        primary = Color(0xFF2A5FB0),
        secondary = Color(0xFF52514E),
        background = Color(0xFFF6F5F2),
        surface = Color(0xFFF6F5F2),
        surfaceVariant = Color(0xFFE9E8E3),
        surfaceContainerLowest = Color(0xFFFCFCFB),
        surfaceContainerLow = Color(0xFFF9F9F7),
        surfaceContainer = Color(0xFFF1F0EC),
        surfaceContainerHigh = Color(0xFFECEBE6),
        surfaceContainerHighest = Color(0xFFE6E5DF),
        outlineVariant = Color(0xFFDDDCD5),
    )
private val DarkScheme =
    darkColorScheme(
        primary = Color(0xFF9DC0F5),
        secondary = Color(0xFFC3C2B7),
        background = Color(0xFF111110),
        surface = Color(0xFF111110),
        surfaceVariant = Color(0xFF2C2C2A),
        surfaceContainerLowest = Color(0xFF0D0D0D),
        surfaceContainerLow = Color(0xFF1A1A19),
        surfaceContainer = Color(0xFF1E1E1D),
        surfaceContainerHigh = Color(0xFF242423),
        surfaceContainerHighest = Color(0xFF2C2C2A),
        outlineVariant = Color(0xFF383835),
    )

@Composable
fun DacheTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = platformColorScheme(dark) ?: if (dark) DarkScheme else LightScheme
    CompositionLocalProvider(
        LocalStatusColors provides if (dark) DarkStatus else LightStatus,
        LocalChartColors provides if (dark) DarkChart else LightChart,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
