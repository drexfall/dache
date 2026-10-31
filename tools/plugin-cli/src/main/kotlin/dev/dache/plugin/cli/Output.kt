package dev.dache.plugin.cli

import dev.dache.core.model.CiRun
import dev.dache.core.model.Monitor
import dev.dache.core.model.PermissionCheck
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.Resource
import dev.dache.core.model.Server
import dev.dache.core.model.ServiceResource
import dev.dache.core.model.Site
import dev.dache.core.model.TokenCheck

/** What a command printed, and whether it succeeded. */
class Outcome(
    val text: String,
    val ok: Boolean,
)

/** Whom the token belongs to and each permission it has, one line each. */
fun describe(check: TokenCheck): String =
    buildString {
        appendLine(check.displayName?.let { "token works for $it" } ?: "the provider refused the token")
        check.permissions.forEach { appendLine("  ${mark(it)} ${it.permission.name} (${need(it)})") }
    }.trimEnd()

private fun mark(check: PermissionCheck): String =
    when (check.granted) {
        true -> "[x]"
        false -> "[ ]"
        null -> "[?]"
    }

private fun need(check: PermissionCheck): String =
    listOfNotNull(
        if (check.permission.required) "required" else "optional",
        "missing".takeIf { check.granted == false },
        "couldn't check".takeIf { check.granted == null },
    ).joinToString(", ")

/** The unified models a refresh produced, grouped by kind or service, one line per resource. */
fun describe(snapshot: ProviderSnapshot): String =
    buildString {
        snapshot.resources.groupBy(::group).forEach { (group, items) ->
            appendLine("$group (${items.size})")
            items.forEach { appendLine("  ${line(it)}") }
        }
        snapshot.billing?.let {
            appendLine("billing: ${it.monthToDateUsage} ${it.currency} this month, balance ${it.accountBalance}")
        }
        if (snapshot.missingPermissions.isNotEmpty()) {
            appendLine("left out, the token lacks: ${snapshot.missingPermissions.sorted().joinToString()}")
        }
        if (isEmpty()) append("nothing")
    }.trimEnd()

private fun group(resource: Resource): String =
    (resource as? ServiceResource)?.serviceLabel ?: resource.kind.name.lowercase()

private fun line(resource: Resource): String =
    listOfNotNull(resource.displayName, status(resource), resource.webUrl).joinToString("  ")

private fun status(resource: Resource): String? =
    when (resource) {
        is Site -> resource.latestDeployment?.status?.name
        is CiRun -> resource.status.name
        is Monitor -> resource.status.name
        is Server -> resource.status.name
        is ServiceResource -> resource.status?.name
        else -> null
    }
