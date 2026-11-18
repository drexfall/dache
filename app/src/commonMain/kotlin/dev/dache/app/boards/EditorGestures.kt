package dev.dache.app.boards

import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType

/** How a press on something draggable turned out. */
enum class PressOutcome {
    /** Released without moving: a tap. */
    TAP,

    /** Held (touch) or moved (mouse, pen): start dragging. */
    DRAG,

    /** A touch that moved before the long press: someone scrolling, so leave it to the scroll container. */
    SCROLL,
}

/**
 * Waits to see what a press that went [down] is for. A mouse drags as soon as it moves; a finger has to hold
 * still for a long press first, so a quick swipe still scrolls the list or canvas under it. Nothing is consumed.
 */
suspend fun AwaitPointerEventScope.awaitPressOutcome(down: PointerInputChange): PressOutcome {
    val touch = down.type == PointerType.Touch
    var outcome: PressOutcome? = null
    val decided =
        withTimeoutOrNull(if (touch) viewConfiguration.longPressTimeoutMillis else Long.MAX_VALUE) {
            while (outcome == null) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                outcome =
                    when {
                        change == null || !change.pressed -> PressOutcome.TAP
                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop ->
                            if (touch) PressOutcome.SCROLL else PressOutcome.DRAG
                        else -> null
                    }
            }
            outcome
        }
    return decided ?: PressOutcome.DRAG
}

/**
 * Follows pointer [pointerId] until it lifts, consuming every move so nothing scrolls underneath, and reports
 * each position. Returns false if the gesture was cancelled, e.g. the pointer vanished.
 */
suspend fun AwaitPointerEventScope.followPointer(
    pointerId: androidx.compose.ui.input.pointer.PointerId,
    onMove: (PointerInputChange) -> Unit,
): Boolean {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == pointerId } ?: return false
        if (!change.pressed) {
            change.consume()
            return true
        }
        // Report first: a consumed change reports no movement.
        onMove(change)
        change.consume()
    }
}
