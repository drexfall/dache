package dev.dache.app.ui.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.action_open_navigation
import dev.dache.app.resources.action_refresh
import org.jetbrains.compose.resources.stringResource

/**
 * Shared page frame. Compact: menu button for the modal sidebar and pull to refresh. Expanded: the sidebar is
 * always visible, so no menu button, and a thin progress bar replaces the pull indicator. [header] sits under
 * the app bar and stays put while the body scrolls.
 */
@Composable
fun PageScaffold(
    title: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenMenu: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    header: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val compact = LocalFormFactor.current == FormFactor.COMPACT
    Scaffold(
        modifier = modifier,
        floatingActionButton = floatingActionButton,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            subtitle?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (onOpenMenu != null) {
                            IconButton(onClick = onOpenMenu) {
                                Icon(
                                    Icons.Filled.Menu,
                                    contentDescription = stringResource(Res.string.action_open_navigation),
                                )
                            }
                        }
                    },
                    actions = {
                        actions()
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(Res.string.action_refresh))
                        }
                    },
                )
                header?.invoke()
                if (!compact && isRefreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
    ) { padding ->
        val body: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier =
                        Modifier
                            .widthIn(max = 1440.dp)
                            .fillMaxWidth()
                            .padding(horizontal = if (compact) 16.dp else 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content,
                )
            }
        }
        if (compact) {
            PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.padding(padding)) {
                body()
            }
        } else {
            Box(Modifier.padding(padding)) { body() }
        }
    }
}
