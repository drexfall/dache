package dev.dache.app.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import dev.dache.core.model.ResourceRef

/** In-app navigation for deep components (e.g. a linked-resource chip), so callbacks need not be threaded through. */
interface AppNavigator {
    fun openAccount(accountId: String)

    /** Opens the resource's own page, extending the breadcrumb from the current page. */
    fun openResource(ref: ResourceRef)

    fun openManageAccounts()
}

val LocalAppNavigator = staticCompositionLocalOf<AppNavigator> { error("No AppNavigator provided") }
