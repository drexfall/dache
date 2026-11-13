package dev.dache.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import dev.dache.core.model.ResourceRef

/**
 * Goes back one step for the platform's back button and for [backRequests]: closes the drawer when it's open,
 * otherwise pops the stack. With one page open and no drawer, the platform's own behaviour (leaving the app on
 * Android) applies.
 */
@Composable
fun BackNavigation(
    stackState: MutableState<BackStack>,
    backRequests: BackRequests?,
    closeDrawer: (() -> Unit)?,
) {
    var stack by stackState
    val back: () -> Unit = { closeDrawer?.invoke() ?: run { stack = stack.pop() } }
    PlatformBackHandler(enabled = closeDrawer != null || stack.size > 1, onBack = back)
    if (backRequests != null) {
        LaunchedEffect(backRequests, closeDrawer) { backRequests.requests.collect { back() } }
    }
}

/** The [AppNavigator] over the back stack: every way to open a page pushes it. */
class StateNavigator(
    private val stack: MutableState<BackStack>,
) : AppNavigator {
    override fun openAccount(accountId: String) = open(Destination.Account(accountId))

    override fun openResource(ref: ResourceRef) = open(Destination.Resource(ref))

    override fun openManageAccounts() = open(Destination.ManageAccounts)

    private fun open(destination: Destination) {
        stack.value = stack.value.push(destination)
    }
}
