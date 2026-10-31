package dev.dache.plugin.runtime

import dev.dache.core.model.StatusLevel
import dev.dache.plugin.api.StatusListSpec
import dev.dache.plugin.api.StatusSpec
import dev.dache.plugin.runtime.mapping.Expression
import dev.dache.plugin.runtime.mapping.ExpressionException
import dev.dache.plugin.runtime.security.HostGuard

/** Checks a status section: an HTTPS URL on a real host, expressions that parse, and known level names. */
internal fun statusProblems(spec: StatusSpec?): List<ManifestProblem> {
    if (spec == null) return emptyList()
    val levels = StatusLevel.entries.map { it.name }.toSet()
    val expressions =
        listOf("status.indicator" to spec.indicator) +
            listOfNotNull(spec.description?.let { "status.description" to it }) +
            listOfNotNull(
                spec.components?.let { "status.components" to it },
                spec.incidents?.let {
                    "status.incidents" to
                        it
                },
            ).flatMap { (path, list) -> listExpressions(path, list) }
    val maps =
        listOf("status.levelMap" to spec.levelMap) +
            listOfNotNull(
                spec.components?.let { "status.components.statusMap" to it.statusMap },
                spec.incidents?.let { "status.incidents.statusMap" to it.statusMap },
            )
    val parsed = HostGuard.parseUrl(spec.url)
    val badUrl =
        parsed == null ||
            !HostGuard.isValidHost(parsed.host) ||
            HostGuard(listOf(parsed.host)).violation(parsed) != null
    return listOfNotNull(
        ManifestProblem("status.url", "must be an https URL on the default port").takeIf { badUrl },
    ) +
        expressions.mapNotNull { (path, source) -> parse(path, source) } +
        maps.flatMap { (path, map) ->
            map.filterValues { it !in levels }.keys.map {
                ManifestProblem(
                    "$path.$it",
                    "must be one of ${levels.joinToString()}",
                )
            }
        }
}

private fun listExpressions(
    path: String,
    list: StatusListSpec,
): List<Pair<String, String>> =
    listOf("$path.items" to list.items) + list.fields.map { (name, source) -> "$path.fields.$name" to source }

private fun parse(
    path: String,
    source: String,
): ManifestProblem? =
    try {
        Expression.parse(source)
        null
    } catch (e: ExpressionException) {
        ManifestProblem(path, e.message.orEmpty())
    }
