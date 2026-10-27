package dev.dache.app.navigation

import androidx.compose.runtime.staticCompositionLocalOf

/** In-app navigation for deep components (e.g. a linked-resource chip), so callbacks need not be threaded through. */
interface AppNavigator {
    fun openAccount(accountId: String)

    fun openManageAccounts()
}

val LocalAppNavigator = staticCompositionLocalOf<AppNavigator> { error("No AppNavigator provided") }
