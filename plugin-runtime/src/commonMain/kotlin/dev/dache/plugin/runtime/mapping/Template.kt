package dev.dache.plugin.runtime.mapping

import kotlinx.serialization.json.JsonElement

/**
 * Text with `{expression}` placeholders, filled from one item of an earlier list: `/projects/{id}/deployments`.
 * Text without placeholders is a template too, and fills to itself.
 */
class Template private constructor(
    val source: String,
    private val parts: List<Part>,
) {
    private sealed interface Part {
        data class Literal(
            val text: String,
        ) : Part

        data class Placeholder(
            val expression: Expression,
        ) : Part
    }

    val placeholders: List<Expression> = parts.filterIsInstance<Part.Placeholder>().map { it.expression }

    /**
     * Evaluates each placeholder against [item] and passes its text through [encode]. Returns null when a
     * placeholder has no text value or [encode] rejects it, since a request with a hole in it can't be sent.
     */
    fun fill(
        item: JsonElement?,
        encode: (String) -> String? = { it },
    ): String? =
        buildString {
            parts.forEach { part ->
                when (part) {
                    is Part.Literal -> append(part.text)
                    is Part.Placeholder -> {
                        val text = item?.let(part.expression::evaluate)?.asText() ?: return null
                        append(encode(text) ?: return null)
                    }
                }
            }
        }

    /** The text with every placeholder replaced by [value], for checks that only care about the shape. */
    fun sample(value: String = "x"): String =
        parts.joinToString("") { part ->
            when (part) {
                is Part.Literal -> part.text
                is Part.Placeholder -> value
            }
        }

    override fun toString(): String = source

    companion object {
        private val placeholder = Regex("\\{([^{}]*)}")

        /** @throws ExpressionException for a bad placeholder expression or a stray brace. */
        fun parse(source: String): Template {
            val parts = mutableListOf<Part>()
            var index = 0
            placeholder.findAll(source).forEach { match ->
                parts += literal(source.substring(index, match.range.first), source)
                parts += Part.Placeholder(Expression.parse(match.groupValues[1]))
                index = match.range.last + 1
            }
            parts += literal(source.substring(index), source)
            return Template(source, parts.filter { it != Part.Literal("") })
        }

        private fun literal(
            text: String,
            source: String,
        ): Part {
            if ('{' in text || '}' in text) throw ExpressionException("unmatched brace in \"$source\"")
            return Part.Literal(text)
        }
    }
}
