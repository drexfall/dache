package dev.dache.app.boards

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.board_done
import dev.dache.app.resources.editor_close
import dev.dache.app.resources.editor_properties
import dev.dache.app.resources.editor_show_library
import dev.dache.app.resources.editor_title
import dev.dache.app.ui.layout.FormFactor
import dev.dache.app.ui.layout.LocalFormFactor
import dev.dache.core.boards.Board
import dev.dache.core.boards.WidgetData
import dev.dache.core.dashboard.DashboardState
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.time.Clock

/**
 * The board editor, a page of its own. Wide screens get three panes: the widget library on the left, the
 * board on a live canvas in the middle and the selected widget's properties on the right. Narrow screens put the
 * canvas on top and the library, or the selected widget's properties, along the bottom. Every change is saved as
 * it's made, so leaving, by Done or by going back, keeps it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardEditorScreen(
    board: Board,
    dashboard: DashboardState,
    actions: BoardActions,
    initialSelection: String?,
    onClose: () -> Unit,
) {
    val state =
        remember(board.id) {
            BoardEditorState(board.widgets, initialSelection, actions::saveWidgets, actions::newWidgetId)
        }
    val geometry = remember { CanvasGeometry() }
    var root by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val compact = LocalFormFactor.current == FormFactor.COMPACT

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.editor_title, board.name), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.editor_close),
                        )
                    }
                },
                actions = { TextButton(onClick = onClose) { Text(stringResource(Res.string.board_done)) } },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().onGloballyPositioned { root = it }) {
            if (compact) {
                Column(Modifier.fillMaxSize()) {
                    EditorCanvas(state, dashboard, geometry, Modifier.weight(1f).fillMaxWidth())
                    HorizontalDivider()
                    Box(Modifier.fillMaxWidth().height(BOTTOM_PANEL_HEIGHT)) {
                        if (state.selected != null) {
                            Column {
                                Row(
                                    Modifier.padding(start = 16.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        stringResource(Res.string.editor_properties),
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    IconButton(onClick = { state.select(null) }) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = stringResource(Res.string.editor_show_library),
                                        )
                                    }
                                }
                                WidgetProperties(state, dashboard, board.name, actions::rename)
                            }
                        } else {
                            WidgetLibrary(state, geometry, horizontal = true, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    WidgetLibrary(
                        state,
                        geometry,
                        horizontal = false,
                        modifier = Modifier.width(LIBRARY_WIDTH).fillMaxHeight(),
                    )
                    VerticalDivider()
                    EditorCanvas(state, dashboard, geometry, Modifier.weight(1f).fillMaxHeight())
                    VerticalDivider()
                    WidgetProperties(
                        state,
                        dashboard,
                        board.name,
                        actions::rename,
                        Modifier.width(PROPERTIES_WIDTH).fillMaxHeight(),
                    )
                }
            }
            DragGhost(state, dashboard, root)
        }
    }
}

/** The widget being dragged, following the pointer above everything else. */
@Composable
private fun DragGhost(
    state: BoardEditorState,
    dashboard: DashboardState,
    root: LayoutCoordinates?,
) {
    val drag = state.drag ?: return
    val coordinates = root?.takeIf { it.isAttached } ?: return
    val density = LocalDensity.current
    val now = remember(dashboard.lastUpdated) { Clock.System.now() }
    val value = remember(drag.widget, dashboard, now) { WidgetData.evaluate(drag.widget, dashboard, now) }
    val topLeft = coordinates.windowToLocal(drag.pointer) - drag.grab
    Box(
        Modifier
            .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
            .size(with(density) { drag.size.width.toDp() }, with(density) { drag.size.height.toDp() })
            .shadow(12.dp, MaterialTheme.shapes.medium)
            .alpha(GHOST_ALPHA)
            .clearAndSetSemantics {},
    ) { WidgetCard(drag.widget, value) }
}

/** The editor for [board], or nothing while it's gone and the stack catches up. */
@Composable
fun BoardEditorPage(
    board: Board?,
    state: DashboardState,
    viewModel: BoardsViewModel,
    initialSelection: String?,
    onClose: () -> Unit,
) {
    board ?: return
    key(board.id) {
        BoardEditorScreen(
            board = board,
            dashboard = state,
            actions = remember(board.id) { viewModel.actionsFor(board.id, onDeleted = onClose) },
            initialSelection = initialSelection,
            onClose = onClose,
        )
    }
}

private val LIBRARY_WIDTH = 260.dp
private val PROPERTIES_WIDTH = 320.dp
private val BOTTOM_PANEL_HEIGHT = 300.dp
private const val GHOST_ALPHA = 0.9f
