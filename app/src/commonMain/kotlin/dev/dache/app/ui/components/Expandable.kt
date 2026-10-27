package dev.dache.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.state_collapsed
import dev.dache.app.resources.state_expanded
import org.jetbrains.compose.resources.stringResource

/** Chevron that points down when collapsed and up when expanded. Decorative; the header carries the state. */
@Composable
fun ExpandChevron(
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(if (expanded) HALF_TURN else 0f)
    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = modifier.rotate(rotation))
}

/**
 * A full-width header that toggles [expanded]. At least 48dp tall, announced as a button with an
 * expanded/collapsed state, and ends in a chevron.
 */
@Composable
fun ExpandableHeader(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val state = stringResource(if (expanded) Res.string.state_expanded else Res.string.state_collapsed)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = state },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
        ExpandChevron(expanded)
    }
}

/** Animated show/hide for expandable bodies. */
@Composable
fun ColumnScope.ExpandableBody(
    expanded: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) { content() }
}

private const val HALF_TURN = 180f
