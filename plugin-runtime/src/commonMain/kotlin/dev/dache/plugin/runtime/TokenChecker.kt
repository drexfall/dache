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

    private val probes =
        resources.flatMap { resource ->
            listOfNotNull(resource.permission?.let { Probe(it, !resource.optional, resource, null) }) +
                resource.requested.values.mapNotNull { field ->
                    field.permission?.let { Probe(it, !field.optional, resource, field) }
                }
        }

    /**
     * Every permission the check reports on, in order: the validate request's first, then the lists'. One
     * permission used by several lists appears once, required if any of them is.
     */
    val declared: List<TokenPermission> =
        (
            listOfNotNull(validatePermission?.let { TokenPermission(it, required = true) }) +
                probes.groupBy { it.permission }.map { (name, group) ->
                    TokenPermission(name, group.any { it.required })
                }
        ).distinctBy { it.name }

    suspend fun check(account: AccountContext): List<PermissionCheck> {
        val results = fanOut(probes) { granted(it, account) }
        val byPermission = probes.zip(results).groupBy({ (probe, _) -> probe.permission }, { (_, granted) -> granted })
        return declared.map { permission ->
            val granted =
                if (permission.name ==
                    validatePermission
                ) {
                    true
                } else {
                    merge(byPermission.getValue(permission.name))
                }
            PermissionCheck(permission, granted)
        }
    }

    /** The validate request was refused: its permission is missing, and nothing else could be checked. */
    fun refused(): List<PermissionCheck> =
        declared.map { PermissionCheck(it, granted = false.takeIf { _ -> it.name == validatePermission }) }

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
