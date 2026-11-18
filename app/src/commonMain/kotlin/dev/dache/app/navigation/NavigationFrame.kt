package dev.dache.app.navigation

import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dache.app.ui.layout.FormFactor
import kotlinx.coroutines.launch

/**
 * The page with the sidebar beside it on wide screens, in a drawer on narrow ones, or on its own when
 * [fullScreen]. Back closes an open drawer before it leaves the page.
 */
@Composable
fun NavigationFrame(
    fullScreen: Boolean,
    formFactor: FormFactor,
    stackState: MutableState<BackStack>,
    backRequests: BackRequests?,
    sidebar: @Composable (onPicked: () -> Unit) -> Unit,
    page: @Composable (onOpenMenu: (() -> Unit)?) -> Unit,
) {
    when {
        fullScreen -> {
            BackNavigation(stackState, backRequests, closeDrawer = null)
            page(null)
        }
        formFactor == FormFactor.EXPANDED -> {
            BackNavigation(stackState, backRequests, closeDrawer = null)
            PermanentNavigationDrawer(
                drawerContent = { PermanentDrawerSheet(Modifier.width(280.dp)) { sidebar {} } },
            ) { page(null) }
        }
        else -> {
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            val closeDrawer: (() -> Unit)? =
                if (drawerState.isOpen) ({ scope.launch { drawerState.close() } }) else null
            BackNavigation(stackState, backRequests, closeDrawer)
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = { ModalDrawerSheet { sidebar { scope.launch { drawerState.close() } } } },
            ) { page { scope.launch { drawerState.open() } } }
        }
    }
}
