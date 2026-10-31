package dev.dache.plugin.runtime

import dev.dache.core.model.ActionInfo
import dev.dache.core.model.ActionInput
import dev.dache.core.model.ActionInputType
import dev.dache.core.model.ActionPreview
import dev.dache.core.model.ActionRequest
import dev.dache.core.model.ActionTarget
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.FieldChange
import dev.dache.plugin.api.ActionSpec
import dev.dache.plugin.api.InputSpec
import dev.dache.plugin.api.InputType
import dev.dache.plugin.api.PluginManifest
import dev.dache.plugin.runtime.http.PluginHttp
import dev.dache.plugin.runtime.mapping.Expression
import dev.dache.plugin.runtime.mapping.ManifestKind
import dev.dache.plugin.runtime.mapping.Mapping
import dev.dache.plugin.runtime.mapping.Template
import dev.dache.plugin.runtime.mapping.asText
import dev.dache.plugin.runtime.security.Redactor
import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** An action can't run: a missing or malformed input, or a target the last refresh didn't list. */
class ActionInputException(
    message: String,
) : Exception(message)

/**
 * Runs a manifest's actions. Each refresh hands over the items it listed ([remember]), so an action reads its
 * target exactly as the provider described it, parent included; nothing is fetched just to fill an action.
 */
internal class ActionExecutor(
    private val manifest: PluginManifest,
    private val http: PluginHttp,
) {
    private val specs = manifest.actions.associateBy { it.id }
    private val targets = MutableStateFlow<Map<String, Map<String, JsonElement>>>(emptyMap())

    val infos: List<ActionInfo> =
        manifest.actions.map { spec ->
            val onDeployments = spec.target == ActionRules.DEPLOYMENT
            ActionInfo(
                id = spec.id,
                label = spec.label,
                kind = checkNotNull(ManifestKind.named(if (onDeployments) "site" else spec.target)?.resourceKind),
                onDeployments = onDeployments,
                destructive = spec.destructive,
                description = spec.description,
                inputs =
                    spec.inputs.map {
                        ActionInput(it.key, it.label, it.type.toCore(), it.required, it.options)
                    },
                permission = spec.permission,
            )
        }

    /** The items one refresh of [accountId] listed, by [targetKey]. */
    fun remember(
        accountId: String,
        items: Map<String, JsonElement>,
    ) {
        targets.update { it + (accountId to items) }
    }

    fun defaults(
        account: ConnectedAccount,
        request: ActionRequest,
    ): Map<String, String> {
        val (spec, item) = resolve(account, request)
        return spec.inputs
            .mapNotNull { input ->
                input.default?.let { Expression.parse(it).evaluate(item)?.asText() }?.let { input.key to it }
            }.toMap()
    }

    fun preview(
        account: ConnectedAccount,
        request: ActionRequest,
        secret: String,
    ): ActionPreview {
        val (spec, item) = resolve(account, request)
        val filled = withInputs(spec, item, request.inputs)
        val url = url(spec, filled, account)
        val body = spec.body?.let { fillBody(it, spec, filled, account.settings, request.inputs) }
        val before = defaults(account, request)
        return ActionPreview(
            action = infos.first { it.id == spec.id },
            method = spec.request.method,
            url = Redactor(manifest.auth, secret).url(url),
            body = body?.let { prettyJson.encodeToString(JsonObject.serializer(), it) },
            changes =
                spec.inputs.mapNotNull { input ->
                    request.inputs[input.key]?.let { FieldChange(input.label, before[input.key], it) }
                },
        )
    }

    suspend fun run(
        account: ConnectedAccount,
        request: ActionRequest,
        secret: String,
    ) {
        val (spec, item) = resolve(account, request)
        val filled = withInputs(spec, item, request.inputs)
        val body = spec.body?.let { fillBody(it, spec, filled, account.settings, request.inputs) }
        http.execute(spec, url(spec, filled, account), body, secret)
    }

    private fun resolve(
        account: ConnectedAccount,
        request: ActionRequest,
    ): Pair<ActionSpec, JsonElement> {
        val spec =
            specs[request.actionId] ?: throw ActionInputException("${manifest.name} has no action ${request.actionId}")
        val key = targetKey(spec.target, request.target)
        val item =
            targets.value[account.id]?.get(key)
                ?: throw ActionInputException("Refresh first: this item wasn't in the last refresh.")
        return spec to item
    }

    /** The target with the inputs under `input`, so `{input.key}` reads them like any expression. */
    private fun withInputs(
        spec: ActionSpec,
        item: JsonElement,
        inputs: Map<String, String>,
    ): JsonElement {
        spec.inputs.firstNotNullOfOrNull { input -> inputProblem(input, inputs[input.key]) }?.let {
            throw ActionInputException(it)
        }
        val fields = (item as? JsonObject)?.toMap().orEmpty()
        return JsonObject(fields + (INPUT to JsonObject(inputs.mapValues { JsonPrimitive(it.value) })))
    }

    private fun url(
        spec: ActionSpec,
        item: JsonElement,
        account: ConnectedAccount,
    ): Url =
        CompiledList(spec.request, items = null).url(item, account.settings)
            ?: throw ActionInputException("The target is missing a value this action needs.")

    /** String values are templates; one that is only a number or boolean input becomes that type. */
    private fun fillBody(
        element: JsonElement,
        spec: ActionSpec,
        item: JsonElement,
        settings: Map<String, String>,
        inputs: Map<String, String>,
    ): JsonObject = fill(element, spec, item, settings, inputs) as JsonObject

    private fun fill(
        element: JsonElement,
        spec: ActionSpec,
        item: JsonElement,
        settings: Map<String, String>,
        inputs: Map<String, String>,
    ): JsonElement =
        when (element) {
            is JsonObject -> JsonObject(element.mapValues { fill(it.value, spec, item, settings, inputs) })
            is JsonArray -> JsonArray(element.map { fill(it, spec, item, settings, inputs) })
            is JsonPrimitive ->
                if (!element.isString) {
                    element
                } else {
                    typedInput(element.content, spec, inputs)
                        ?: JsonPrimitive(
                            Template.parse(element.content).fill(item, settings)
                                ?: throw ActionInputException("The target is missing a value this action needs."),
                        )
                }
        }

    private fun typedInput(
        source: String,
        spec: ActionSpec,
        inputs: Map<String, String>,
    ): JsonPrimitive? {
        val key = wholeInput.matchEntire(source.trim())?.groupValues?.get(1)
        val input = spec.inputs.firstOrNull { it.key == key }
        val value = key?.let(inputs::get)
        return if (input == null || value == null) {
            null
        } else {
            when (input.type) {
                InputType.NUMBER -> value.toLongOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(value.toDouble())
                InputType.BOOLEAN -> JsonPrimitive(value.toBooleanStrict())
                InputType.TEXT -> null
            }
        }
    }

    companion object {
        private const val INPUT = "input"
        private val wholeInput = Regex("""\{\s*input\.([A-Za-z0-9_]+)\s*}""")
        private val prettyJson = Json { prettyPrint = true }

        fun targetKey(
            target: String,
            at: ActionTarget,
        ): String =
            if (target == ActionRules.DEPLOYMENT) {
                "${ActionRules.DEPLOYMENT}:${at.ref.nativeId}:${at.deploymentId}"
            } else {
                "$target:${at.ref.nativeId}"
            }

        /** Index entries for [records] of [kind]: each item, and for sites each deployment with its site as `^`. */
        fun index(
            kind: ManifestKind,
            records: List<Mapping.Record>,
        ): Map<String, JsonElement> =
            records
                .flatMap { record ->
                    val id = record.text("nativeId") ?: return@flatMap emptyList()
                    val own = listOf("${kind.kindName}:$id" to record.item)
                    val deployments =
                        if (kind == ManifestKind.SITE) {
                            record.records("deployments").mapNotNull { deploy ->
                                val deployId = deploy.text("nativeId") ?: return@mapNotNull null
                                val fields = (deploy.item as? JsonObject)?.toMap().orEmpty()
                                "${ActionRules.DEPLOYMENT}:$id:$deployId" to JsonObject(fields + ("^" to record.item))
                            }
                        } else {
                            emptyList()
                        }
                    own + deployments
                }.toMap()
    }
}

private fun InputType.toCore(): ActionInputType =
    when (this) {
        InputType.TEXT -> ActionInputType.TEXT
        InputType.NUMBER -> ActionInputType.NUMBER
        InputType.BOOLEAN -> ActionInputType.BOOLEAN
    }

/** Why [value] won't do for [input], or null when it will. */
private fun inputProblem(
    input: InputSpec,
    value: String?,
): String? =
    when {
        input.required && value.isNullOrBlank() -> "${input.label} is required."
        value == null -> null
        input.type == InputType.NUMBER && value.toDoubleOrNull() == null -> "${input.label} must be a number."
        input.type == InputType.BOOLEAN && value.toBooleanStrictOrNull() == null ->
            "${input.label} must be true or false."
        input.options.isNotEmpty() && value !in input.options ->
            "${input.label} must be one of ${input.options.joinToString()}."
        else -> null
    }
