package dev.dache.app.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dache.app.boards.BoardIconImage
import dev.dache.app.profiles.ProfileActions
import dev.dache.app.profiles.ProfileSwitcher
import dev.dache.app.resources.Res
import dev.dache.app.resources.action_account_settings
import dev.dache.app.resources.app_name
import dev.dache.app.resources.board_open_editor
import dev.dache.app.resources.context_open
import dev.dache.app.resources.nav_accounts_header
import dev.dache.app.resources.nav_add_account
import dev.dache.app.resources.nav_boards_header
import dev.dache.app.resources.nav_manage_accounts
import dev.dache.app.resources.nav_new_board
import dev.dache.app.resources.nav_no_accounts
import dev.dache.app.resources.nav_plugins
import dev.dache.app.resources.nav_problems
import dev.dache.app.resources.nav_refresh_failed
import dev.dache.app.resources.open_in
import dev.dache.app.service.pageTitle
import dev.dache.app.service.problemsOn
import dev.dache.app.ui.components.ContextMenuArea
import dev.dache.app.ui.components.ContextMenuItem
import dev.dache.app.ui.components.LocalLinkOpener
import dev.dache.app.ui.components.OpenInNewIcon
import dev.dache.core.boards.Board
import dev.dache.core.dashboard.DashboardState
import dev.dache.core.dashboard.DashboardSummary
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.Profiles
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.ServicePage
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/**
 * The profile switcher, the active profile's boards, then one entry per account in the profile, then account
 * management. The first board is home, so it carries the count of every problem.
 */
@Composable
fun Sidebar(
    state: DashboardState,
    current: Destination,
    onSelect: (Destination) -> Unit,
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier,
    profiles: Profiles = Profiles(),
    profileActions: ProfileActions = ProfileActions.None,
    boards: List<Board> = emptyList(),
    onNewBoard: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        Text(
            stringResource(Res.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
        )
        ProfileSwitcher(
            profiles = profiles,
            accountCount = state.accounts.size,
            actions = profileActions,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        BoardItems(boards, current, onSelect, onNewBoard, state.summary.problemCount)

        Text(
            stringResource(Res.string.nav_accounts_header),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
        )
        if (state.accounts.isEmpty()) {
            Text(
                stringResource(Res.string.nav_no_accounts),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
        }
        val now = Clock.System.now()
        state.accounts.forEach { account ->
            val result = state.results.firstOrNull { it.account.id == account.id }
            val problems = result?.snapshot?.let { DashboardSummary.from(listOf(it), now).problemCount } ?: 0
            ContextMenuArea(accountMenu(account, onSelect)) { menuActions ->
                NavigationDrawerItem(
                    modifier = menuActions,
                    label = {
                        Column {
                            Text(account.providerId.displayName, maxLines = 1)
                            Text(
                                account.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    icon = { ProviderAvatar(account.providerId, accountId = account.id) },
                    selected = current == Destination.Account(account.id),
                    onClick = { onSelect(Destination.Account(account.id)) },
                    badge = {
                        if (result?.error != null) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = stringResource(Res.string.nav_refresh_failed),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            ProblemBadge(problems)
                        }
                    },
                )
            }
            // The open account's pages, so moving between them is one tap.
            if (current.accountId == account.id) {
                ServicePageItems(account.id, state.pages(account.providerId), result?.snapshot, current, onSelect)
            }
        }
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.nav_add_account)) },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            selected = false,
            onClick = onAddAccount,
        )
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.nav_manage_accounts)) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            selected = current == Destination.ManageAccounts,
            onClick = { onSelect(Destination.ManageAccounts) },
        )
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.nav_plugins)) },
            icon = { Icon(Icons.Filled.Build, contentDescription = null) },
            selected = current == Destination.Plugins,
            onClick = { onSelect(Destination.Plugins) },
        )
    }
}

/** The active profile's boards, home first, then a way to make another. */
@Composable
private fun BoardItems(
    boards: List<Board>,
    current: Destination,
    onSelect: (Destination) -> Unit,
    onNewBoard: (() -> Unit)?,
    totalProblems: Int,
) {
    Text(
        stringResource(Res.string.nav_boards_header),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
    )
    boards.forEachIndexed { index, board ->
        val destination = Destination.Board(board.id)
        val home = index == 0
        ContextMenuArea(boardMenu(board, onSelect)) { menuActions ->
            NavigationDrawerItem(
                label = { Text(board.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                icon = { BoardIconImage(board.icon) },
                selected = current == destination || (home && current == Destination.Home),
                onClick = { onSelect(destination) },
                badge = if (home) ({ ProblemBadge(totalProblems) }) else null,
                modifier = menuActions,
            )
        }
    }
    if (onNewBoard != null) {
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.nav_new_board)) },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            selected = false,
            onClick = onNewBoard,
        )
    }
}

/** A right click on a board: open it, or open it in the editor, with the board to go back to. */
@Composable
private fun boardMenu(
    board: Board,
    onSelect: (Destination) -> Unit,
): List<ContextMenuItem> =
    listOf(
        ContextMenuItem(stringResource(Res.string.context_open)) { onSelect(Destination.Board(board.id)) },
        ContextMenuItem(stringResource(Res.string.board_open_editor), icon = Icons.Filled.Edit) {
            onSelect(Destination.Board(board.id))
            onSelect(Destination.BoardEditor(board.id))
        },
    )

/** A right click on an account: open it or its settings, or the provider's own dashboard when there is one. */
@Composable
private fun accountMenu(
    account: ConnectedAccount,
    onSelect: (Destination) -> Unit,
): List<ContextMenuItem> {
    val opener = LocalLinkOpener.current
    val dashboard = account.providerId.dashboardUrl
    return listOfNotNull(
        ContextMenuItem(stringResource(Res.string.context_open)) { onSelect(Destination.Account(account.id)) },
        ContextMenuItem(stringResource(Res.string.action_account_settings), icon = Icons.Filled.Settings) {
            onSelect(Destination.AccountSettings(account.id))
        },
        dashboard?.let { url ->
            ContextMenuItem(
                stringResource(Res.string.open_in, account.providerId.displayName),
                icon = OpenInNewIcon,
                startsGroup = true,
            ) { opener.open(url) }
        },
    )
}

@Composable
private fun ServicePageItems(
    accountId: String,
    pages: List<ServicePage>,
    snapshot: ProviderSnapshot?,
    current: Destination,
    onSelect: (Destination) -> Unit,
) {
    val now = Clock.System.now()
    pages.forEach { page ->
        val destination = Destination.Service(accountId, page.id)
        NavigationDrawerItem(
            label = { Text(pageTitle(page), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            selected = current == destination,
            onClick = { onSelect(destination) },
            badge = { ProblemBadge(snapshot?.problemsOn(page, now) ?: 0) },
            modifier = Modifier.padding(start = 40.dp),
        )
    }
}

@Composable
private fun ProblemBadge(count: Int) {
    if (count <= 0) return
    val description = stringResource(Res.string.nav_problems, count)
    Badge(modifier = Modifier.padding(start = 4.dp)) {
        Text(count.toString(), modifier = Modifier.clearAndSetSemantics { contentDescription = description })
    }
}
