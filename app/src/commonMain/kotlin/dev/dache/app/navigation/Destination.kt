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

    /** One of an account's pages beside its dashboard, such as DNS, named by the provider's page id. */
    data class Service(
        val accountId: String,
        val pageId: String,
    ) : Destination

    /** An account's connection and what its token can do. */
    data class AccountSettings(
        val accountId: String,
    ) : Destination

    /** One resource, reached through a linked-resource chip. [from] is the page the chip was on. */
    data class Resource(
        val ref: ResourceRef,
        val from: Destination,
    ) : Destination
}

/** Every page from where the user started to this one, for the breadcrumb. An account's own pages sit under it. */
val Destination.trail: List<Destination>
    get() =
        when (this) {
            is Destination.Resource -> from.trail + this
            is Destination.Service -> listOf(Destination.Account(accountId), this)
            is Destination.AccountSettings -> listOf(Destination.Account(accountId), this)
            else -> listOf(this)
        }

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
            Destination.Overview, Destination.ManageAccounts -> null
        }

/** Opens [ref] from this page. A resource already on the trail is gone back to, so linked loops don't pile up. */
fun Destination.openResource(ref: ResourceRef): Destination =
    trail.firstOrNull { it is Destination.Resource && it.ref == ref } ?: Destination.Resource(ref, this)

val DestinationSaver: Saver<Destination, List<String>> =
    Saver(
        save = { destination -> ArrayList(destination.trail.flatMap { it.tokens() }) },
        restore = { tokens -> runCatching { decode(tokens) }.getOrDefault(Destination.Overview) },
    )

private fun Destination.tokens(): List<String> =
    when (this) {
        Destination.Overview -> listOf("overview")
        Destination.ManageAccounts -> listOf("accounts")
        is Destination.Account -> listOf("account", accountId)
        is Destination.Service -> listOf("service", accountId, pageId)
        is Destination.AccountSettings -> listOf("settings", accountId)
        is Destination.Resource -> listOf("resource", ref.providerId.name, ref.accountId, ref.kind.name, ref.nativeId)
    }

/** Rebuilds the last page of a saved trail. Each resource page goes back to the page before it. */
private fun decode(tokens: List<String>): Destination {
    val rest = ArrayDeque(tokens)
    var destination: Destination = Destination.Overview
    while (rest.isNotEmpty()) {
        destination =
            when (rest.removeFirst()) {
                "accounts" -> Destination.ManageAccounts
                "account" -> Destination.Account(rest.removeFirst())
                "service" -> Destination.Service(rest.removeFirst(), rest.removeFirst())
                "settings" -> Destination.AccountSettings(rest.removeFirst())
                "resource" -> {
                    val provider = ProviderId.valueOf(rest.removeFirst())
                    val accountId = rest.removeFirst()
                    val kind = ResourceKind.valueOf(rest.removeFirst())
                    Destination.Resource(ResourceRef(provider, accountId, rest.removeFirst(), kind), destination)
                }
                else -> Destination.Overview
            }
    }
    return destination
}
