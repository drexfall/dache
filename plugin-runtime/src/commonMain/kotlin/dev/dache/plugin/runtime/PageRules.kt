package dev.dache.plugin.runtime

import dev.dache.plugin.api.FieldSpec
import dev.dache.plugin.api.PluginManifest
import dev.dache.plugin.api.ResourceSpec
import dev.dache.plugin.runtime.mapping.ManifestKind

/**
 * Checks how a manifest's resources are presented and permissioned: service specs, permissions on optional
 * lists, and that pages name real resources and leave none out.
 */
internal object PageRules {
    private val pageIdPattern = Regex("[a-z][a-z0-9-]{0,39}")
    private val serviceIdPattern = Regex("[a-z][A-Za-z0-9]{0,39}")

    fun check(manifest: PluginManifest): List<ManifestProblem> =
        buildList {
            manifest.validate.permission?.let { addAll(permission("validate.permission", it)) }
            manifest.resources.forEachIndexed { index, resource ->
                val path = "resources[$index]"
                addAll(service(path, resource))
                addAll(permissions(path, resource))
            }
            val ids = manifest.resources.mapNotNull { it.service?.id }
            ids.groupBy { it }.filterValues { it.size > 1 }.keys.forEach {
                add(ManifestProblem("resources", "service id \"$it\" is used twice"))
            }
            addAll(pages(manifest))
        }

    private fun service(
        path: String,
        resource: ResourceSpec,
    ): List<ManifestProblem> {
        val spec = resource.service
        val isService = resource.kind == ManifestKind.SERVICE.kindName
        return when {
            spec == null && isService -> listOf(ManifestProblem("$path.service", "a service resource needs a service"))
            spec == null -> emptyList()
            !isService -> listOf(ManifestProblem("$path.service", "only service resources have one"))
            else ->
                listOfNotNull(
                    ManifestProblem("$path.service.id", "must match ${serviceIdPattern.pattern}")
                        .takeUnless { serviceIdPattern.matches(spec.id) },
                    ManifestProblem("$path.service.id", "\"${spec.id}\" is a kind name")
                        .takeIf { ManifestKind.named(spec.id) != null },
                    ManifestProblem("$path.service.label", "must not be blank").takeIf { spec.label.isBlank() },
                )
        }
    }

    private fun permissions(
        path: String,
        resource: ResourceSpec,
    ): List<ManifestProblem> =
        optional(path, resource.optional, resource.permission) +
            resource.fields.flatMap { (name, field) ->
                val nested = field as? FieldSpec.Nested ?: return@flatMap emptyList()
                val fieldPath = "$path.fields.$name"
                optional(fieldPath, nested.optional, nested.permission) +
                    listOfNotNull(
                        ManifestProblem("$fieldPath.optional", "only a field with a request can be optional")
                            .takeIf { nested.optional && nested.request == null },
                        ManifestProblem("$fieldPath.permission", "only a field with a request needs one")
                            .takeIf { nested.permission != null && nested.request == null },
                    )
            }

    /** An optional list names its permission, so a refusal can be explained. */
    private fun optional(
        path: String,
        optional: Boolean,
        permission: String?,
    ): List<ManifestProblem> =
        when {
            permission != null -> permission("$path.permission", permission)
            optional -> listOf(ManifestProblem("$path.permission", "an optional list must name its permission"))
            else -> emptyList()
        }

    private fun permission(
        path: String,
        name: String,
    ): List<ManifestProblem> = listOfNotNull(ManifestProblem(path, "must not be blank").takeIf { name.isBlank() })

    private fun pages(manifest: PluginManifest): List<ManifestProblem> {
        if (manifest.pages.isEmpty()) return emptyList()
        val keys = manifest.resources.map { it.key }.toSet()
        val problems = mutableListOf<ManifestProblem>()
        manifest.pages.forEachIndexed { index, page ->
            val path = "pages[$index]"
            if (!pageIdPattern.matches(page.id)) {
                problems +=
                    ManifestProblem("$path.id", "must match ${pageIdPattern.pattern}")
            }
            if (page.title.isBlank()) problems += ManifestProblem("$path.title", "must not be blank")
            if (page.resources.isEmpty()) problems += ManifestProblem("$path.resources", "name at least one resource")
            page.resources.filter { it !in keys }.forEach {
                problems += ManifestProblem("$path.resources", "\"$it\" is not a kind or service id of any resource")
            }
        }
        manifest.pages.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach {
            problems += ManifestProblem("pages", "page id \"$it\" is used twice")
        }
        val shown = manifest.pages.flatMap { it.resources }.toSet()
        (keys - shown).forEach { problems += ManifestProblem("pages", "\"$it\" is on no page") }
        return problems
    }
}
