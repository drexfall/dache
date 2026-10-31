package dev.dache.plugin.runtime.mapping

import dev.dache.plugin.api.FieldSpec
import kotlinx.serialization.json.JsonElement
import kotlin.time.Instant

/** A resource's or nested record's field specs with every expression parsed. Build only from validated specs. */
class Mapping(
    fields: Map<String, FieldSpec>,
    private val statusMap: Map<String, String>,
) {
    private sealed interface Compiled {
        class Single(
            val source: FieldSource,
        ) : Compiled

        class Multi(
            val sources: List<FieldSource>,
        ) : Compiled

        class Nested(
            val from: Expression,
            val mapping: Mapping,
            val requested: Boolean,
        ) : Compiled
    }

    private val compiled: Map<String, Compiled> =
        fields.mapValues { (_, spec) ->
            when (spec) {
                is FieldSpec.Expr -> Compiled.Single(FieldSource.parse(spec.expr))
                is FieldSpec.ExprList -> Compiled.Multi(spec.exprs.map(FieldSource::parse))
                is FieldSpec.Nested ->
                    Compiled.Nested(
                        Expression.parse(spec.from),
                        Mapping(spec.fields, spec.statusMap),
                        requested = spec.request != null,
                    )
            }
        }

    /**
     * Reads mapped fields from one item of a response. [settings] fill `{@key}` placeholders. [fetched] holds the
     * items of each nested field that has its own request, fetched for this item; a requested field missing from
     * it has no records.
     */
    inner class Record(
        private val element: JsonElement,
        private val settings: Map<String, String> = emptyMap(),
        private val fetched: Map<String, List<JsonElement>> = emptyMap(),
    ) {
        /** The item as the provider listed it, for actions that read the target. */
        val item: JsonElement get() = element

        private fun value(field: String): Value? =
            (compiled[field] as? Compiled.Single)?.source?.evaluate(element, settings)

        fun text(field: String): String? = value(field)?.asText()

        fun int(field: String): Int? = value(field)?.asInt()

        fun number(field: String): Double? = value(field)?.asDouble()

        fun time(field: String): Instant? = value(field)?.asInstant()

        /** The status name [field]'s provider value maps to; null when it is missing or not in the statusMap. */
        fun status(field: String): String? = text(field)?.let(statusMap::get)

        fun textList(field: String): List<String> =
            when (val spec = compiled[field]) {
                is Compiled.Single ->
                    spec.source
                        .evaluate(element, settings)
                        ?.asTextList()
                        .orEmpty()
                is Compiled.Multi -> spec.sources.flatMap { it.evaluate(element, settings)?.asTextList().orEmpty() }
                else -> emptyList()
            }

        fun records(field: String): List<Mapping.Record> {
            val spec = compiled[field] as? Compiled.Nested ?: return emptyList()
            val items =
                if (spec.requested) {
                    fetched[field].orEmpty()
                } else {
                    when (val value = spec.from.evaluate(element)) {
                        is Value.Items -> value.values
                        is Value.Record -> listOf(value.value)
                        else -> emptyList()
                    }
                }
            return items.map { spec.mapping.Record(it, settings) }
        }
    }
}

/** One field value: an expression, or a template when it has `{placeholders}`. Templates always produce text. */
class FieldSource private constructor(
    val expression: Expression?,
    val template: Template?,
) {
    fun evaluate(
        element: JsonElement,
        settings: Map<String, String>,
    ): Value? = template?.fill(element, settings)?.let(Value::Text) ?: expression?.evaluate(element)

    companion object {
        /** @throws ExpressionException */
        fun parse(source: String): FieldSource =
            if ('{' in
                source
            ) {
                FieldSource(null, Template.parse(source))
            } else {
                FieldSource(Expression.parse(source), null)
            }
    }
}
