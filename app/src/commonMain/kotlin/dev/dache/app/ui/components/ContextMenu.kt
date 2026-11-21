package dev.dache.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round

/**
 * One entry in a [ContextMenuArea]. A [destructive] entry is drawn in the error colour, and one that [startsGroup]
 * has a divider above it.
 */
class ContextMenuItem(
    val label: String,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val startsGroup: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Wraps [content] so a secondary click (a right click, or a two-finger click on a trackpad) opens [items] as a
 * menu at the pointer. The menu key or Shift+F10 opens it under the focused content. [content] gets a modifier
 * to put on its clickable element: it offers the same entries to accessibility services as custom actions on that
 * element, so nothing here is mouse-only. A touch long press is left alone. [onOpen] runs each time the menu
 * opens. With no items it draws [content] and nothing else.
 */
@Composable
fun ContextMenuArea(
    items: List<ContextMenuItem>,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit = {},
    content: @Composable (actions: Modifier) -> Unit,
) {
    if (items.isEmpty()) {
        Box(modifier, propagateMinConstraints = true) { content(Modifier) }
        return
    }
    var anchor by remember { mutableStateOf<Offset?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val currentOnOpen by rememberUpdatedState(onOpen)
    val open = { at: Offset ->
        anchor = at
        currentOnOpen()
    }
    Box(
        modifier
            .onSizeChanged { size = it }
            .pointerInput(Unit) { awaitSecondaryPresses(open) }
            .onKeyEvent { event ->
                if (event.opensContextMenu()) {
                    open(Offset(0f, size.height.toFloat()))
                    true
                } else {
                    false
                }
            },
        propagateMinConstraints = true,
    ) {
        content(
            Modifier.semantics {
                customActions =
                    items.filter { it.enabled }.map { item ->
                        CustomAccessibilityAction(item.label) {
                            item.onClick()
                            true
                        }
                    }
            },
        )
        // A layer that doesn't take part in sizing, holding a zero-size anchor where the menu should open.
        Box(Modifier.matchParentSize()) {
            anchor?.let { at ->
                Box(Modifier.offset { at.round() }.size(0.dp)) {
                    ContextMenu(items, onDismiss = { anchor = null })
                }
            }
        }
    }
}

@Composable
private fun ContextMenu(
    items: List<ContextMenuItem>,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        items.forEachIndexed { index, item ->
            if (item.startsGroup && index > 0) HorizontalDivider()
            // Unspecified leaves the item's own colours, including its disabled one.
            val color = if (item.destructive && item.enabled) MaterialTheme.colorScheme.error else Color.Unspecified
            DropdownMenuItem(
                text = { Text(item.label, color = color) },
                leadingIcon = item.icon?.let { icon -> { Icon(icon, contentDescription = null) } },
                enabled = item.enabled,
                onClick = {
                    onDismiss()
                    item.onClick()
                },
            )
        }
    }
}

/**
 * Reports where each secondary-button press lands, and consumes it before anything inside sees it, so a right
 * click on a row opens the menu without also clicking the row.
 */
private suspend fun PointerInputScope.awaitSecondaryPresses(onPress: (Offset) -> Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                val change = event.changes.firstOrNull() ?: continue
                event.changes.forEach { it.consume() }
                onPress(change.position)
            }
        }
    }
}

private fun KeyEvent.opensContextMenu(): Boolean =
    type == KeyEventType.KeyDown && (key == Key.Menu || (key == Key.F10 && isShiftPressed))
