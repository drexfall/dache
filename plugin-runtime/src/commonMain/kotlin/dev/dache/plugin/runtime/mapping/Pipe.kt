package dev.dache.plugin.runtime.mapping

import kotlin.time.Instant

/**
 * The mapping language's functions. Kept deliberately small: a pipe is added only when a real plugin needs it.
 * Every pipe passes null through except [default], and returns null when the input has the wrong type.
 */
enum class Pipe(
    val pipeName: String,
    val takesArgument: Boolean,
) {
    /** URL or hostname to its lowercase hostname, without port or credentials. */
    HOST("host", takesArgument = false) {
        override fun apply(
            value: Value?,
            argument: String?,
        ): Value? = (value as? Value.Text)?.value?.let(::hostOf)?.let(Value::Text)
    },

    LOWER("lower", takesArgument = false) {
        override fun apply(
            value: Value?,
            argument: String?,
        ): Value? = (value as? Value.Text)?.let { Value.Text(it.value.lowercase()) }
    },

    /** ISO-8601 timestamp, with `Z` or an offset, to a point in time. */
    DATE("date", takesArgument = false) {
        override fun apply(
            value: Value?,
            argument: String?,
        ): Value? =
            (value as? Value.Text)?.let { text ->
                runCatching { Instant.parse(text.value) }.getOrNull()?.let(Value::Time)
            }
    },

    /** Replaces null or an empty string with the literal argument: `default:unknown`. */
    DEFAULT("default", takesArgument = true) {
        override fun apply(
            value: Value?,
            argument: String?,
        ): Value? = if (value == null || value == Value.Text("")) Value.Text(argument.orEmpty()) else value
    },
    ;

    abstract fun apply(
        value: Value?,
        argument: String?,
    ): Value?

    companion object {
        private val byName = entries.associateBy { it.pipeName }

        fun named(name: String): Pipe? = byName[name]
    }
}

private fun hostOf(text: String): String? {
    val afterScheme = text.substringAfter("://", text)
    val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    val host =
        authority
            .substringAfterLast('@')
            .let { if (it.startsWith("[")) it.substringBefore(']') + "]" else it.substringBefore(':') }
            .trimEnd('.')
            .lowercase()
    return host.ifEmpty { null }
}
