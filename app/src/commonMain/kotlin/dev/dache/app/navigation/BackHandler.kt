package dev.dache.app.navigation

import androidx.compose.runtime.Composable

/**
 * Runs [onBack] when the platform asks to go back: the Android back button or gesture. Platforms without one
 * do nothing; the desktop gets back requests from the window's key handler instead, see [BackRequests].
 */
@Composable
expect fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
)
