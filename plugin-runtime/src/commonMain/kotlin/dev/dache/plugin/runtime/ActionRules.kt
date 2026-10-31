package dev.dache.plugin.runtime

import dev.dache.plugin.api.ActionSpec
import dev.dache.plugin.api.PluginManifest
import dev.dache.plugin.runtime.mapping.Expression
import dev.dache.plugin.runtime.mapping.ExpressionException
import dev.dache.plugin.runtime.mapping.ManifestKind
import dev.dache.plugin.runtime.mapping.Template
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Checks a manifest's actions: what they target, their method, placeholders, inputs and body. */
internal class ActionRules(
    private val manifest: PluginManifest,
    private val placeholders: PlaceholderRules,
) {
    private val producedKinds = manifest.resources.map { it.kind }.toSet()

    fun check(): List<ManifestProblem> =
        manifest.actions.flatMapIndexed { index, action -> check("actions[$index]", action) }

    private fun check(
        path: String,
        action: ActionSpec,
    ): List<ManifestProblem> {
        val inputKeys = action.inputs.map { it.key }.toSet()
        return listOfNotNull(
            problem("$path.id", "must match ${idPattern.pattern}") { !idPattern.matches(action.id) },
            problem("$path.id", "\"${action.id}\" is declared twice") {
                manifest.actions.count { it.id == action.id } >
                    1
            },
            problem("$path.label", "must not be blank") { action.label.isBlank() },
            problem("$path.target", targetMessage(action.target)) { !targetProduced(action.target) },
            problem("$path.request.method", "must be one of ${writeMethods.joinToString()}") {
                action.request.method !in writeMethods
            },
            problem("$path.request.paging", "actions don't page") { action.request.paging != null },
            problem("$path.request.headers", "actions can't set headers") { action.request.headers.isNotEmpty() },
            problem("$path.permission", "a destructive action must name its permission") {
                action.destructive && action.permission == null
            },
        ) + placeholders.url(action.request.url, fannedOut = true).map { ManifestProblem("$path.request.url", it) } +
            action.request.query.flatMap { (name, value) ->
                placeholders
                    .query(
                        name,
                        value,
                        fannedOut = true,
                    ).map { ManifestProblem("$path.request.query.$name", it) }
            } + inputProblems(path, action) + referenceProblems(path, action, inputKeys)
    }

    private fun targetProduced(target: String): Boolean =
        if (target == DEPLOYMENT) {
            ManifestKind.SITE.kindName in producedKinds
        } else {
            ManifestKind.named(target)?.resourceKind != null && target in producedKinds
        }

    private fun targetMessage(target: String): String =
        "\"$target\" isn't a kind this plugin lists; use one of its resource kinds, or deployment for a site's"

    private fun inputProblems(
        path: String,
        action: ActionSpec,
    ): List<ManifestProblem> =
        action.inputs.flatMapIndexed { index, input ->
            val at = "$path.inputs[$index]"
            listOfNotNull(
                problem("$at.key", "must match ${inputKey.pattern}") { !inputKey.matches(input.key) },
                problem("$at.key", "\"${input.key}\" is declared twice") {
                    action.inputs.count { it.key == input.key } >
                        1
                },
                problem("$at.label", "must not be blank") { input.label.isBlank() },
                input.default?.let { expression -> parseProblem("$at.default", expression) },
            )
        }

    /** Every `{input.key}` names a declared input, and every template in the body parses. */
    private fun referenceProblems(
        path: String,
        action: ActionSpec,
        inputKeys: Set<String>,
    ): List<ManifestProblem> {
        val templates =
            listOf("$path.request.url" to action.request.url) +
                action.request.query.map { (name, value) -> "$path.request.query.$name" to value } +
                action.body?.let { strings("$path.body", it) }.orEmpty()
        return templates.flatMap { (at, source) ->
            val template =
                try {
                    Template.parse(source)
                } catch (e: ExpressionException) {
                    return@flatMap listOf(ManifestProblem(at, e.message.orEmpty()))
                }
            template.placeholders
                .map { it.source }
                .filter { it.startsWith(INPUT_PREFIX) && it.removePrefix(INPUT_PREFIX) !in inputKeys }
                .map { ManifestProblem(at, "{$it} names no input") }
        }
    }

    private fun strings(
        path: String,
        element: JsonElement,
    ): List<Pair<String, String>> =
        when (element) {
            is JsonObject -> element.flatMap { (key, value) -> strings("$path.$key", value) }
            is JsonArray -> element.flatMapIndexed { index, value -> strings("$path[$index]", value) }
            is JsonPrimitive -> if (element.isString) listOf(path to element.content) else emptyList()
        }

    private fun parseProblem(
        path: String,
        expression: String,
    ): ManifestProblem? =
        try {
            Expression.parse(expression)
            null
        } catch (e: ExpressionException) {
            ManifestProblem(path, e.message.orEmpty())
        }

    private inline fun problem(
        path: String,
        message: String,
        failed: () -> Boolean,
    ): ManifestProblem? = if (failed()) ManifestProblem(path, message) else null

    companion object {
        const val DEPLOYMENT = "deployment"
        const val INPUT_PREFIX = "input."
        val writeMethods = listOf("POST", "PUT", "PATCH", "DELETE")
        private val idPattern = Regex("[a-z][a-z0-9-]{0,39}")
        private val inputKey = Regex("[a-z][A-Za-z0-9_]{0,39}")
    }
}
