package dev.dache.plugin.runtime.mapping

import dev.dache.core.model.DeployStatus
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
            val expression: Expression,
        ) : Compiled

        class Multi(
            val expressions: List<Expression>,
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
                is FieldSpec.Expr -> Compiled.Single(Expression.parse(spec.expr))
                is FieldSpec.ExprList -> Compiled.Multi(spec.exprs.map(Expression::parse))
                is FieldSpec.Nested ->
                    Compiled.Nested(
                        Expression.parse(spec.from),
                        Mapping(spec.fields, spec.statusMap),
                        requested = spec.request != null,
                    )
            }
        }

    /**
     * Reads mapped fields from one item of a response. [fetched] holds the items of each nested field that has
     * its own request, fetched for this item; a requested field missing from it has no records.
     */
    inner class Record(
        private val element: JsonElement,
        private val fetched: Map<String, List<JsonElement>> = emptyMap(),
    ) {
        private fun value(field: String): Value? = (compiled[field] as? Compiled.Single)?.expression?.evaluate(element)

        fun text(field: String): String? = value(field)?.asText()

        fun int(field: String): Int? = value(field)?.asInt()

        fun time(field: String): Instant? = value(field)?.asInstant()

        /** Unmapped and unlisted provider values are [DeployStatus.UNKNOWN]. */
        fun status(field: String): DeployStatus =
            text(field)
                ?.let(statusMap::get)
                ?.let { name -> DeployStatus.entries.firstOrNull { it.name == name } }
                ?: DeployStatus.UNKNOWN

        fun textList(field: String): List<String> =
            when (val spec = compiled[field]) {
                is Compiled.Single ->
                    spec.expression
                        .evaluate(element)
                        ?.asTextList()
                        .orEmpty()
                is Compiled.Multi -> spec.expressions.flatMap { it.evaluate(element)?.asTextList().orEmpty() }
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
            return items.map { spec.mapping.Record(it) }
        }
    }
}
