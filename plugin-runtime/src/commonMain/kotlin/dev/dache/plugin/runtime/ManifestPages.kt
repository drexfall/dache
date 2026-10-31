package dev.dache.plugin.runtime

import dev.dache.core.model.ServicePage
import dev.dache.plugin.api.PluginManifest
import dev.dache.plugin.api.ResourceSpec
import dev.dache.plugin.runtime.mapping.ManifestKind

/** The pages a manifest declares, or one per kind and service when it declares none. */
internal fun manifestPages(manifest: PluginManifest): List<ServicePage> {
    val resources = manifest.resources
    if (manifest.pages.isNotEmpty()) return manifest.pages.map { page(it.id, it.title, it.resources, resources) }
    return resources
        .filter { it.onPages }
        .map { it.key }
        .distinct()
        .map { key ->
            val label = resources.firstNotNullOfOrNull { resource -> resource.service?.takeIf { it.id == key }?.label }
            page(key, label, listOf(key), resources)
        }
}

private fun page(
    id: String,
    title: String?,
    keys: List<String>,
    resources: List<ResourceSpec>,
): ServicePage {
    val shown = resources.filter { it.key in keys }
    return ServicePage(
        id = id,
        title = title,
        kinds = shown.filter { it.service == null }.mapNotNull { ManifestKind.named(it.kind)?.resourceKind }.toSet(),
        services = shown.mapNotNull { it.service?.id }.toSet(),
        permissions = shown.flatMap { it.permissions }.toSet(),
    )
}
