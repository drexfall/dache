package dev.dache.app.navigation

import androidx.compose.runtime.saveable.Saver
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ResourceKind
import dev.dache.core.model.ResourceRef

/** Pages reachable from the sidebar. Every connected account is its own page. */
sealed interface Destination {
    data object Overview : Destination

    data class Account(
        val accountId: String,
    ) : Destination

    data object ManageAccounts : Destination

    /** Plugins in use, the registry and sideloading. */
    data object Plugins : Destination

    /** One of an account's pages beside its dashboard, such as DNS, named by the provider's page id. */
    data class Service(
        val accountId: String,
        val pageId: String,
    ) : Destination

    /** An account's connection and what its token can do. */
    data class AccountSettings(
        val accountId: String,
    ) : Destination

    /** One resource, reached through a linked-resource chip or a row. */
    data class Resource(
        val ref: ResourceRef,
    ) : Destination
}

/**
 * The pages open, oldest first; the last one is shown. Never empty: the first entry is where the app started.
 * Going back pops the last entry, so the Android back button and Esc on desktop retrace the user's steps, and
 * the breadcrumb is the stack itself.
 */
typealias BackStack = List<Destination>

val BackStack.current: Destination get() = last()

/**
 * Opens [destination]. A page already open is gone back to instead of opened again, so loops through linked
 * resources don't pile up; one of an account's pages replaces a sibling page of the same account on top, so
 * moving between DNS and Workers doesn't bury the account under its own pages.
 */
fun BackStack.push(destination: Destination): BackStack {
    val existing = indexOf(destination)
    if (existing >= 0) return subList(0, existing + 1)
    val top = last()
    return if (destination.isSiblingOf(top)) dropLast(1) + destination else this + destination
}

/** Back to the previous page; the first page stays. */
fun BackStack.pop(): BackStack = if (size > 1) dropLast(1) else this

/** Drops every page about an account that is gone, so removing an account can't leave its pages open. */
fun BackStack.keepingAccounts(accountIds: Collection<String>): BackStack =
    filter { it.accountId == null || it.accountId in accountIds }.ifEmpty { listOf(Destination.Overview) }

private fun Destination.isSiblingOf(other: Destination): Boolean =
    isAccountPage && other.isAccountPage && accountId == other.accountId

/** Service pages and settings sit under their account rather than beside it. */
private val Destination.isAccountPage: Boolean
    get() = this is Destination.Service || this is Destination.AccountSettings

/** The sidebar entry to highlight: a resource page and the settings belong to their account. */
val Destination.sidebarEntry: Destination
    get() =
        when (this) {
            is Destination.Resource -> Destination.Account(ref.accountId)
            is Destination.AccountSettings -> Destination.Account(accountId)
            else -> this
        }

/** The account this page is about, if any. */
val Destination.accountId: String?
    get() =
        when (this) {
            is Destination.Account -> accountId
            is Destination.Service -> accountId
            is Destination.AccountSettings -> accountId
            is Destination.Resource -> ref.accountId
            Destination.Overview, Destination.ManageAccounts, Destination.Plugins -> null
        }

/** Saves the whole stack as a flat token list, so rotation and process death restore every page. */
val BackStackSaver: Saver<BackStack, List<String>> =
    Saver(
        save = { stack -> ArrayList(stack.flatMap { it.tokens() }) },
        restore = { tokens -> runCatching { decode(tokens) }.getOrDefault(listOf(Destination.Overview)) },
    )

private fun Destination.tokens(): List<String> =
    when (this) {
        Destination.Overview -> listOf("overview")
        Destination.ManageAccounts -> listOf("accounts")
        Destination.Plugins -> listOf("plugins")
        is Destination.Account -> listOf("account", accountId)
        is Destination.Service -> listOf("service", accountId, pageId)
        is Destination.AccountSettings -> listOf("settings", accountId)
        is Destination.Resource -> listOf("resource", ref.providerId.id, ref.accountId, ref.kind.name, ref.nativeId)
    }

private fun decode(tokens: List<String>): BackStack {
    val rest = ArrayDeque(tokens)
    val stack = mutableListOf<Destination>()
    while (rest.isNotEmpty()) {
        stack +=
            when (rest.removeFirst()) {
                "overview" -> Destination.Overview
                "accounts" -> Destination.ManageAccounts
                "plugins" -> Destination.Plugins
                "account" -> Destination.Account(rest.removeFirst())
                "service" -> Destination.Service(rest.removeFirst(), rest.removeFirst())
                "settings" -> Destination.AccountSettings(rest.removeFirst())
                "resource" -> {
                    val provider = ProviderId(rest.removeFirst())
                    val accountId = rest.removeFirst()
                    val kind = ResourceKind.valueOf(rest.removeFirst())
                    Destination.Resource(ResourceRef(provider, accountId, rest.removeFirst(), kind))
                }
                else -> error("unknown page")
            }
    }
    return stack.ifEmpty { listOf(Destination.Overview) }
}
