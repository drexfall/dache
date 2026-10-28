package dev.dache.plugin.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * How one target field is filled. In JSON it is written as:
 * - a string: one expression, e.g. `"created_at | date"`;
 * - an array of strings: expressions whose results are concatenated into a list, nulls dropped;
 * - an object: a nested list of records, e.g. a site's deployments.
 */
@Serializable(with = FieldSpecSerializer::class)
sealed interface FieldSpec {
    data class Expr(
        val expr: String,
    ) : FieldSpec

    data class ExprList(
        val exprs: List<String>,
    ) : FieldSpec

    /**
     * Maps the object or array at [from] with [fields]. A single object becomes a one-item list.
     *
     * With [request], the engine sends it once per item, its placeholders filled from that item, and [from] is
     * read from the response instead of the item (e.g. a project's deployments from their own endpoint).
     */
    @Serializable
    data class Nested(
        val from: String,
        val fields: Map<String, FieldSpec>,
        val statusMap: Map<String, String> = emptyMap(),
        val request: RequestSpec? = null,
    ) : FieldSpec
}

internal object FieldSpecSerializer : KSerializer<FieldSpec> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("dev.dache.plugin.api.FieldSpec")

    private val stringList = ListSerializer(String.serializer())

    override fun deserialize(decoder: Decoder): FieldSpec {
        val input = decoder as? JsonDecoder ?: throw SerializationException("FieldSpec can only be read from JSON")
        return when (val element = input.decodeJsonElement()) {
            is JsonPrimitive ->
                if (element.isString) {
                    FieldSpec.Expr(element.content)
                } else {
                    throw SerializationException("A field expression must be a string, got ${element.content}")
                }
            is JsonArray -> FieldSpec.ExprList(element.map { it.jsonPrimitive.content })
            is JsonObject -> input.json.decodeFromJsonElement(FieldSpec.Nested.serializer(), element)
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: FieldSpec,
    ) {
        val output = encoder as? JsonEncoder ?: throw SerializationException("FieldSpec can only be written as JSON")
        val element: JsonElement =
            when (value) {
                is FieldSpec.Expr -> JsonPrimitive(value.expr)
                is FieldSpec.ExprList -> output.json.encodeToJsonElement(stringList, value.exprs)
                is FieldSpec.Nested -> output.json.encodeToJsonElement(FieldSpec.Nested.serializer(), value)
            }
        output.encodeJsonElement(element)
    }
}
