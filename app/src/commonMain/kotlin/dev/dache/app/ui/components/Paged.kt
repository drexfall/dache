package dev.dache.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.page_next
import dev.dache.app.resources.page_previous
import dev.dache.app.resources.page_range
import org.jetbrains.compose.resources.stringResource

/** How many rows any list shows at once before it pages. */
const val PAGE_SIZE = 10

/**
 * Shows [items] [pageSize] at a time, with "11–20 of 481" and previous/next controls when there is more than one
 * page. [key] keeps the page across recompositions and configuration changes; a list that shrinks below the
 * current page falls back to its last page.
 */
@Composable
fun <T> Paged(
    items: List<T>,
    key: String,
    modifier: Modifier = Modifier,
    pageSize: Int = PAGE_SIZE,
    spacing: Dp = 0.dp,
    item: @Composable (T) -> Unit,
) {
    val pages = maxOf(1, (items.size + pageSize - 1) / pageSize)
    var page by rememberSaveable(key) { mutableIntStateOf(0) }
    val current = page.coerceIn(0, pages - 1)
    val from = current * pageSize
    val shown = items.subList(from, minOf(items.size, from + pageSize))
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        shown.forEach { item(it) }
        if (pages > 1) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.page_range, from + 1, from + shown.size, items.size),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
                IconButton(onClick = { page = current - 1 }, enabled = current > 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(Res.string.page_previous))
                }
                IconButton(onClick = { page = current + 1 }, enabled = current < pages - 1) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(Res.string.page_next))
                }
            }
        }
    }
}
