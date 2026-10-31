package dev.dache.plugin.runtime.mapping

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A parsed mapping expression: `path.to.field | pipe | pipe:argument`.
 *
 * The path is dot-separated keys, with numeric segments indexing into arrays (`deploys.0.id`). A key applied to
 * an array reads it from every object in the array (`alias.domain` is each alias's domain), skipping the ones
 * without it. A `key=value` segment keeps the objects of an array whose key has that value, so
 * `networks.v4.type=public.0.ip_address` is the first public address. `$` alone is the element itself. Pipes run
 * left to right.
 *
 * An item fetched once per item of another list (a fan-out or a nested request) carries that item as `^`, so
 * `^.name` is the parent's name and `^.^.id` its parent's id.
 */
class Expression private constructor(
    val source: String,
    private val path: List<String>,
    val pipes: List<PipeCall>,
) {
    data class PipeCall(
        val pipe: Pipe,
        val argument: String?,
    )

    fun evaluate(element: JsonElement): Value? =
        pipes.fold(resolve(element)?.toValue()) { value, call -> call.pipe.apply(value, call.argument) }

    private fun resolve(element: JsonElement): JsonElement? =
        path.fold(element as JsonElement?) { current, segment ->
            when {
                '=' in segment -> (current as? JsonArray)?.let { filter(it, segment) }
                current is JsonObject -> current[segment]
                current is JsonArray -> segment.toIntOrNull()?.let(current::getOrNull) ?: project(current, segment)
                else -> null
            }
        }

    /** The objects in [array] whose key, the part of [segment] before `=`, has the text after it. */
    private fun filter(
        array: JsonArray,
        segment: String,
    ): JsonArray {
        val key = segment.substringBefore('=')
        val value = segment.substringAfter('=')
        return JsonArray(array.filter { ((it as? JsonObject)?.get(key) as? JsonPrimitive)?.content == value })
    }

    /** [segment] read from every object in [array]; null for a numeric [segment], which is an index. */
    private fun project(
        array: JsonArray,
        segment: String,
    ): JsonArray? =
        if (segment.toIntOrNull() != null) {
            null
        } else {
            JsonArray(array.mapNotNull { item -> (item as? JsonObject)?.get(segment)?.takeUnless { it is JsonNull } })
        }

    override fun toString(): String = source

    companion object {
        /** The key a fanned-out item keeps the item it was fetched for under. */
        const val PARENT = "^"

        private val keyPattern = Regex("[A-Za-z_][A-Za-z0-9_-]*(=[A-Za-z0-9_-]+)?|[0-9]+|\\^")

        /** @throws ExpressionException with a message that says what is wrong with [source]. */
        fun parse(source: String): Expression {
            val parts = source.split('|').map { it.trim() }
            val pathText = parts.first()
            if (pathText.isEmpty()) throw ExpressionException("missing path in \"$source\"")
            val path =
                if (pathText == "$") {
                    emptyList()
                } else {
                    pathText.split('.').onEach { segment ->
                        if (!keyPattern.matches(segment)) {
                            throw ExpressionException("invalid path segment \"$segment\" in \"$source\"")
                        }
                    }
                }
            return Expression(source, path, parts.drop(1).map { parsePipe(it, source) })
        }

        private fun parsePipe(
            text: String,
            source: String,
        ): PipeCall {
            val name = text.substringBefore(':').trim()
            val argument = if (':' in text) text.substringAfter(':').trim() else null
            val pipe = Pipe.named(name)
            val problem =
                when {
                    pipe == null ->
                        "unknown pipe \"$name\" in \"$source\"; available: ${Pipe.entries.joinToString { it.pipeName }}"
                    pipe.takesArgument && argument == null -> "pipe \"$name\" needs an argument, e.g. $name:value"
                    !pipe.takesArgument && argument != null -> "pipe \"$name\" takes no argument"
                    else -> null
                }
            if (pipe == null || problem != null) throw ExpressionException(problem.orEmpty())
            return PipeCall(pipe, argument)
        }
    }
}

class ExpressionException(
    message: String,
) : IllegalArgumentException(message)
