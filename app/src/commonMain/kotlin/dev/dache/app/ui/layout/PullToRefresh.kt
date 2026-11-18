package dev.dache.app.ui.layout

import androidx.compose.runtime.staticCompositionLocalOf

/** Whether this platform is touch-first, so pages refresh on a pull at every width: true on Android. */
expect val platformPullToRefresh: Boolean

/** Whether [PageScaffold] offers pull to refresh. The platform decides, not the form factor; tests override it. */
val LocalPullToRefresh = staticCompositionLocalOf { platformPullToRefresh }
