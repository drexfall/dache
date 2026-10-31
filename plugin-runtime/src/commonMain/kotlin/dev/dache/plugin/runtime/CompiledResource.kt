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

/** Whether the resource is shown on a page. Billing isn't: it belongs to the account's dashboard. */
internal val ResourceSpec.onPages: Boolean get() = ManifestKind.named(kind)?.resourceKind != null

/** The token permissions a resource's requests need, its nested ones included. */
internal val ResourceSpec.permissions: List<String>
    get() = listOfNotNull(permission) + fields.values.mapNotNull { (it as? FieldSpec.Nested)?.permission }

/**
 * Runs [fetch]. A required list's failure fails the refresh. An optional list's failure, whatever it is, comes
 * back as a failed result instead, so one product the account doesn't use, or one bad endpoint, can't take the
 * rest of the account down with it.
 */
internal suspend fun <T> readable(
    optional: Boolean,
    fetch: suspend () -> T,
): Result<T> =
    try {
        Result.success(fetch())
    } catch (e: ProviderError) {
        if (!optional) throw e
        Result.failure(e)
    }

/**
 * The provider said no to this list for this token: a 401 or 403, or a 404 for a product the account doesn't
 * have. Anything else, like a server error or a rate limit, is a failure to load.
 */
internal val Throwable.isRefusal: Boolean
    get() = this is ProviderError.MissingScope || this is ProviderError.Unauthorized || this is ProviderError.NotFound
