package dev.dache.plugin.runtime.mapping

import kotlinx.serialization.json.JsonElement

/**
 * Text with placeholders: `{expression}` is filled from one item of an earlier list, `{@key}` from the account's
 * settings, e.g. `/teams/{@team}/projects/{id}`. Text without placeholders is a template too, and fills to itself.
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

        data class Setting(
            val key: String,
        ) : Part
    }

    /** Placeholders filled from an item. */
    val placeholders: List<Expression> = parts.filterIsInstance<Part.Placeholder>().map { it.expression }

    /** Keys of the settings this template uses. */
    val settings: List<String> = parts.filterIsInstance<Part.Setting>().map { it.key }

    /** The setting key when the template is nothing but `{@key}`. */
    val soleSetting: String? = (parts.singleOrNull() as? Part.Setting)?.key

    /**
     * Fills each placeholder from [item] or [settings] and passes its text through [encode]. Returns null when a
     * placeholder has no text value (a blank setting counts as none) or [encode] rejects it, since a request or
     * field with a hole in it is no use.
     */
    fun fill(
        item: JsonElement?,
        settings: Map<String, String> = emptyMap(),
        encode: (String) -> String? = { it },
    ): String? =
        buildString {
            parts.forEach { part ->
                val text =
                    when (part) {
                        is Part.Literal -> {
                            append(part.text)
                            return@forEach
                        }
                        is Part.Placeholder -> item?.let(part.expression::evaluate)?.asText()
                        is Part.Setting -> settings[part.key]?.ifBlank { null }
                    }
                append(encode(text ?: return null) ?: return null)
            }
        }

    /** The text with every placeholder replaced by [value], for checks that only care about the shape. */
    fun sample(value: String = "x"): String =
        parts.joinToString("") { part ->
            when (part) {
                is Part.Literal -> part.text
                else -> value
            }
        }

    override fun toString(): String = source

    companion object {
        private val placeholder = Regex("\\{([^{}]*)}")
        private val settingKey = Regex("@([A-Za-z][A-Za-z0-9_]*)")

        /** @throws ExpressionException for a bad placeholder expression or a stray brace. */
        fun parse(source: String): Template {
            val parts = mutableListOf<Part>()
            var index = 0
            placeholder.findAll(source).forEach { match ->
                parts += literal(source.substring(index, match.range.first), source)
                parts += placeholder(match.groupValues[1].trim(), source)
                index = match.range.last + 1
            }
            parts += literal(source.substring(index), source)
            return Template(source, parts.filter { it != Part.Literal("") })
        }

        private fun placeholder(
            text: String,
            source: String,
        ): Part {
            if (!text.startsWith('@')) return Part.Placeholder(Expression.parse(text))
            val key =
                settingKey.matchEntire(text) ?: throw ExpressionException("invalid setting \"$text\" in \"$source\"")
            return Part.Setting(key.groupValues[1])
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
