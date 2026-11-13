package dev.dache.app.navigation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Back requests from outside the UI tree, such as Esc or Alt+Left in the desktop window's key handler. The app
 * collects [requests] and treats each one like the Android back button.
 */
class BackRequests {
    private val flow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val requests: Flow<Unit> = flow

    fun request() {
        flow.tryEmit(Unit)
    }
}
