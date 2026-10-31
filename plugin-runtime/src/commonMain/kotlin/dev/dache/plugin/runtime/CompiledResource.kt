package dev.dache.plugin.runtime

import dev.dache.core.model.ProviderError
import dev.dache.plugin.api.FieldSpec
import dev.dache.plugin.api.ResourceSpec
import dev.dache.plugin.runtime.mapping.ManifestKind
import dev.dache.plugin.runtime.mapping.Mapping

/** A resource spec with its requests and mapping parsed. Build only from validated specs. */
internal class CompiledResource(
    spec: ResourceSpec,
) {
    val kind = checkNotNull(ManifestKind.named(spec.kind)) { "manifest was not validated" }
    val service = spec.service
    val list = CompiledList(spec.request, spec.items)
    val parents = spec.forEach?.let { CompiledList(it.request, it.items) }
    val mapping = Mapping(spec.fields, spec.statusMap)
    val permission = spec.permission
    val optional = spec.optional

    /** Nested fields with their own request, sent once per item. */
    val requested: Map<String, RequestedField> =
        spec.fields
            .mapNotNull { (name, field) ->
                val nested = field as? FieldSpec.Nested
                nested?.request?.let { name to RequestedField(CompiledList(it, nested.from), nested) }
            }.toMap()
}

internal class RequestedField(
    val list: CompiledList,
    spec: FieldSpec.Nested,
) {
    val permission = spec.permission
    val optional = spec.optional
}

/** What pages call a resource: its service id, or its kind name. */
internal val ResourceSpec.key: String get() = service?.id ?: kind

/** The token permissions a resource's requests need, its nested ones included. */
internal val ResourceSpec.permissions: List<String>
    get() = listOfNotNull(permission) + fields.values.mapNotNull { (it as? FieldSpec.Nested)?.permission }

/**
 * Runs [fetch]. When [optional], a list the token can't read (403) or that doesn't exist (404) gives null
 * instead of failing.
 */
internal suspend fun <T> readable(
    optional: Boolean,
    fetch: suspend () -> T,
): T? =
    try {
        fetch()
    } catch (e: ProviderError.MissingScope) {
        if (optional) null else throw e
    } catch (e: ProviderError.NotFound) {
        if (optional) null else throw e
    }
