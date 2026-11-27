package dev.dache.app.boards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dache.app.navigation.iconPainter
import dev.dache.app.resources.Res
import dev.dache.app.resources.board_icon_label
import dev.dache.app.resources.board_icon_not_image
import dev.dache.app.resources.board_icon_symbol_bell
import dev.dache.app.resources.board_icon_symbol_calendar
import dev.dache.app.resources.board_icon_symbol_cart
import dev.dache.app.resources.board_icon_symbol_check
import dev.dache.app.resources.board_icon_symbol_gear
import dev.dache.app.resources.board_icon_symbol_heart
import dev.dache.app.resources.board_icon_symbol_home
import dev.dache.app.resources.board_icon_symbol_info
import dev.dache.app.resources.board_icon_symbol_list
import dev.dache.app.resources.board_icon_symbol_lock
import dev.dache.app.resources.board_icon_symbol_person
import dev.dache.app.resources.board_icon_symbol_pin
import dev.dache.app.resources.board_icon_symbol_search
import dev.dache.app.resources.board_icon_symbol_star
import dev.dache.app.resources.board_icon_symbol_warning
import dev.dache.app.resources.board_icon_symbol_wrench
import dev.dache.app.resources.board_icon_unreadable
import dev.dache.app.resources.board_icon_upload
import dev.dache.app.resources.board_icon_uploaded
import dev.dache.core.boards.BoardIcon
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** One of the app's own board icons: [id] is what a board stores, so it must never change once shipped. */
class BoardSymbol(
    val id: String,
    val vector: ImageVector,
    val label: StringResource,
)

/** The icons a board can have short of an upload, in the order the picker shows them; the first is the default. */
val BoardSymbols: List<BoardSymbol> =
    listOf(
        BoardSymbol("list", Icons.AutoMirrored.Filled.List, Res.string.board_icon_symbol_list),
        BoardSymbol(BoardIcon.HOME, Icons.Filled.Home, Res.string.board_icon_symbol_home),
        BoardSymbol("star", Icons.Filled.Star, Res.string.board_icon_symbol_star),
        BoardSymbol("heart", Icons.Filled.Favorite, Res.string.board_icon_symbol_heart),
        BoardSymbol("check", Icons.Filled.CheckCircle, Res.string.board_icon_symbol_check),
        BoardSymbol("warning", Icons.Filled.Warning, Res.string.board_icon_symbol_warning),
        BoardSymbol("bell", Icons.Filled.Notifications, Res.string.board_icon_symbol_bell),
        BoardSymbol("wrench", Icons.Filled.Build, Res.string.board_icon_symbol_wrench),
        BoardSymbol("gear", Icons.Filled.Settings, Res.string.board_icon_symbol_gear),
        BoardSymbol("lock", Icons.Filled.Lock, Res.string.board_icon_symbol_lock),
        BoardSymbol("person", Icons.Filled.Person, Res.string.board_icon_symbol_person),
        BoardSymbol("calendar", Icons.Filled.DateRange, Res.string.board_icon_symbol_calendar),
        BoardSymbol("search", Icons.Filled.Search, Res.string.board_icon_symbol_search),
        BoardSymbol("pin", Icons.Filled.Place, Res.string.board_icon_symbol_pin),
        BoardSymbol("cart", Icons.Filled.ShoppingCart, Res.string.board_icon_symbol_cart),
        BoardSymbol("info", Icons.Filled.Info, Res.string.board_icon_symbol_info),
    )

/** [icon]'s symbol; the default for none, an upload, or a symbol this version doesn't have. */
fun symbolOf(icon: BoardIcon?): BoardSymbol = BoardSymbols.firstOrNull { it.id == icon?.symbol } ?: BoardSymbols.first()

/** A board's icon: its uploaded image, or its symbol, which also stands in for an image that won't decode. */
@Composable
fun BoardIconImage(
    icon: BoardIcon?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    val density = LocalDensity.current
    val upload = icon?.uploaded
    val painter = remember(upload, density) { upload?.let { iconPainter(it, density) } }
    if (painter != null) {
        Image(
            painter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size).clip(RoundedCornerShape(size * CORNER_FRACTION)),
        )
    } else {
        Icon(symbolOf(icon).vector, contentDescription = null, modifier = modifier.size(size))
    }
}

/**
 * Every [BoardSymbols] entry, then the uploaded image if there is one, then a tile to upload another. Picking
 * calls [onSelect] at once; an upload that isn't a usable image shows why instead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoardIconPicker(
    selected: BoardIcon?,
    onSelect: (BoardIcon) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The last upload stays on offer after picking a symbol, so changing your mind doesn't mean uploading again.
    var upload by remember { mutableStateOf(selected?.takeIf { it.uploaded != null }) }
    var error by remember { mutableStateOf<StringResource?>(null) }
    val pick =
        rememberImageFilePicker(MAX_ICON_FILE_BYTES) { bytes ->
            val icon = bytes?.let(::iconFromUpload)?.let(BoardIcon::ofImage)
            error =
                when {
                    bytes == null -> Res.string.board_icon_unreadable
                    icon == null -> Res.string.board_icon_not_image
                    else -> null
                }
            if (icon != null) {
                upload = icon
                onSelect(icon)
            }
        }
    val current = selected?.takeIf { it.uploaded != null } ?: BoardIcon.ofSymbol(symbolOf(selected).id)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.board_icon_label), style = MaterialTheme.typography.labelLarge)
        FlowRow(
            Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BoardSymbols.forEach { symbol ->
                val icon = BoardIcon.ofSymbol(symbol.id)
                IconTile(stringResource(symbol.label), selected = current == icon, onClick = { onSelect(icon) }) {
                    Icon(symbol.vector, contentDescription = null)
                }
            }
            upload?.let { icon ->
                IconTile(
                    stringResource(Res.string.board_icon_uploaded),
                    selected = current == icon,
                    onClick = { onSelect(icon) },
                ) { BoardIconImage(icon) }
            }
            IconTile(stringResource(Res.string.board_icon_upload), selected = null, onClick = pick) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        }
        error?.let {
            Text(
                stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** A choice in the picker, or with [selected] null a button beside them. */
@Composable
private fun IconTile(
    label: String,
    selected: Boolean?,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val choice =
        if (selected == null) {
            Modifier.clickable(role = Role.Button, onClick = onClick)
        } else {
            Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected == true) colors.secondaryContainer else colors.surface,
        contentColor = if (selected == true) colors.onSecondaryContainer else colors.onSurfaceVariant,
        border =
            if (selected == true) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outlineVariant),
        modifier =
            Modifier
                .size(TILE_SIZE)
                .clip(RoundedCornerShape(8.dp))
                .then(choice)
                .semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

private val TILE_SIZE = 44.dp

/** An uploaded icon's corners are rounded by this share of its size, like an app icon's. */
private const val CORNER_FRACTION = 0.2f
