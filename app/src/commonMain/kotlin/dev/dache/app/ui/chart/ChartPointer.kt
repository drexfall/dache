package dev.dache.app.ui.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Reports the pointer's x position (mouse hover on desktop, touch on mobile) and taps/clicks. A press that
 * moves further than the touch slop is a scroll, not a tap. Events are not consumed, so lists still scroll.
 * Reports null when a mouse leaves the chart.
 */
@Composable
fun Modifier.trackPointerX(
    onX: (Float?) -> Unit,
    onTap: (Float) -> Unit = {},
): Modifier {
    val hover by rememberUpdatedState(onX)
    val tap by rememberUpdatedState(onTap)
    return pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        awaitPointerEventScope {
            var pressedAt: Offset? = null
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: continue
                when (event.type) {
                    PointerEventType.Exit -> hover(null)
                    PointerEventType.Enter, PointerEventType.Move -> {
                        hover(change.position.x)
                        if (pressedAt?.let { (change.position - it).getDistance() > slop } == true) pressedAt = null
                    }
                    PointerEventType.Press -> {
                        hover(change.position.x)
                        pressedAt = change.position
                    }
                    PointerEventType.Release -> {
                        if (pressedAt != null) tap(change.position.x)
                        pressedAt = null
                    }
                    else -> Unit
                }
            }
        }
    }
}
