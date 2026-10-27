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
 * rather than the dynamic theme surface so the contrast checks hold. Failures use the fixed critical status
 * color against the de-emphasis gray (deutan ΔE 9.1); single-series charts use categorical slot 1.
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
    val deemphasis: Color,
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
        deemphasis = Color(0xFF898781),
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
        deemphasis = Color(0xFF898781),
    )

val LocalChartColors = staticCompositionLocalOf { LightChart }

private val LightScheme = lightColorScheme(primary = Color(0xFF3B5BA9), secondary = Color(0xFF5A5D72))
private val DarkScheme = darkColorScheme(primary = Color(0xFFB2C5FF), secondary = Color(0xFFC2C5DD))

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
