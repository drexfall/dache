package dev.dache.plugin.runtime.mapping

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlin.time.Instant

/** What an expression evaluates to. Missing paths and JSON null both evaluate to Kotlin null. */
sealed interface Value {
    data class Text(
        val value: String,
    ) : Value

    /** Kept as the JSON literal so large integer ids survive unchanged. */
    data class Num(
        val literal: String,
    ) : Value

    data class Bool(
        val value: Boolean,
    ) : Value

    data class Time(
        val value: Instant,
    ) : Value

    data class Items(
        val values: List<JsonElement>,
    ) : Value

    data class Record(
        val value: JsonObject,
    ) : Value
}

fun JsonElement.toValue(): Value? =
    when (this) {
        JsonNull -> null
        is JsonPrimitive ->
            if (isString) Value.Text(content) else booleanOrNull?.let(Value::Bool) ?: Value.Num(content)
        is JsonArray -> Value.Items(this)
        is JsonObject -> Value.Record(this)
    }

/** Text for strings, and the literal for numbers and booleans, since ids are often numeric. */
fun Value.asText(): String? =
    when (this) {
        is Value.Text -> value
        is Value.Num -> literal
        is Value.Bool -> value.toString()
        else -> null
    }

/** Whole numbers only; fractional values are truncated. */
fun Value.asInt(): Int? =
    when (this) {
        is Value.Num -> literal.toDoubleOrNull()?.toInt()
        is Value.Text -> value.toDoubleOrNull()?.toInt()
        else -> null
    }

fun Value.asDouble(): Double? =
    when (this) {
        is Value.Num -> literal.toDoubleOrNull()
        is Value.Text -> value.trim().toDoubleOrNull()
        else -> null
    }

fun Value.asInstant(): Instant? = (this as? Value.Time)?.value

/** A list of strings: array items that are primitives, or a single text value as a one-item list. */
fun Value.asTextList(): List<String> =
    when (this) {
        is Value.Items -> values.mapNotNull { it.toValue()?.asText() }
        else -> listOfNotNull(asText())
    }
