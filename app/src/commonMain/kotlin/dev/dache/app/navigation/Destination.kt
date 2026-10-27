package dev.dache.app.navigation

import androidx.compose.runtime.saveable.Saver

/** Pages reachable from the sidebar. Every connected account is its own page. */
sealed interface Destination {
    data object Overview : Destination

    data class Account(
        val accountId: String,
    ) : Destination

    data object ManageAccounts : Destination
}

val DestinationSaver: Saver<Destination, String> =
    Saver(
        save = {
            when (it) {
                Destination.Overview -> "overview"
                Destination.ManageAccounts -> "accounts"
                is Destination.Account -> "account:${it.accountId}"
            }
        },
        restore = {
            when {
                it == "accounts" -> Destination.ManageAccounts
                it.startsWith("account:") -> Destination.Account(it.removePrefix("account:"))
                else -> Destination.Overview
            }
        },
    )
