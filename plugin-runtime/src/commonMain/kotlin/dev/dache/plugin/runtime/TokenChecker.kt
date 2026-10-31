package dev.dache.plugin.runtime

import dev.dache.core.model.PermissionCheck
import dev.dache.core.model.ProviderError
import dev.dache.core.model.TokenPermission
import kotlinx.serialization.json.JsonElement

/**
 * Checks which of a manifest's permissions a token has, by sending the first request of each list that names
 * one. A list that fans out is checked with the first parent item, so checking costs a few requests, not a refresh.
 */
internal class TokenChecker(
    private val lists: ListFetcher,
    private val resources: List<CompiledResource>,
    /** The validate request's permission, granted once validation has passed. */
    private val validatePermission: String?,
) {
    private class Probe(
        val permission: String,
        val required: Boolean,
        val resource: CompiledResource,
        val field: RequestedField?,
    )

    suspend fun check(account: AccountContext): List<PermissionCheck> {
        val probes =
            resources.flatMap { resource ->
                listOfNotNull(resource.permission?.let { Probe(it, !resource.optional, resource, null) }) +
                    resource.requested.values.mapNotNull { field ->
                        field.permission?.let { Probe(it, !field.optional, resource, field) }
                    }
            }
        val results = fanOut(probes) { granted(it, account) }
        val validated = listOfNotNull(validatePermission?.let { PermissionCheck(TokenPermission(it, true), true) })
        val probed =
            probes.zip(results).groupBy { (probe, _) -> probe.permission }.map { (name, group) ->
                val required = group.any { (probe, _) -> probe.required }
                PermissionCheck(TokenPermission(name, required), merge(group.map { (_, granted) -> granted }))
            }
        return (validated + probed).distinctBy { it.permission.name }
    }

    /** Null when there was no parent item to send the request for, or the answer says nothing about permissions. */
    private suspend fun granted(
        probe: Probe,
        account: AccountContext,
    ): Boolean? =
        try {
            val target = probe.field?.list ?: probe.resource.list
            // The lists before the target, each giving the first item as the next one's parent.
            val before = listOfNotNull(probe.resource.parents, probe.resource.list.takeIf { probe.field != null })
            val parent =
                before.fold(listOf<JsonElement?>(null)) { found, list ->
                    found.flatMap { lists.first(list, account, it).take(1) }
                }
            if (parent.isEmpty()) {
                null
            } else {
                lists.first(target, account, parent.single())
                true
            }
        } catch (_: ProviderError.MissingScope) {
            false
        } catch (_: ProviderError.Unauthorized) {
            false
        } catch (_: ProviderError) {
            null
        }

    /** One permission used by several lists: missing if any is refused, granted only if all are allowed. */
    private fun merge(results: List<Boolean?>): Boolean? =
        when {
            false in results -> false
            results.all { it == true } -> true
            else -> null
        }
}
