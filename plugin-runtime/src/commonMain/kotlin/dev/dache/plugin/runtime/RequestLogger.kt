package dev.dache.plugin.runtime

/**
 * One request as it may be logged. Only the runtime can create one, and only through the redactor, so a
 * logger never sees a token. Response bodies are included for debugging and redacted the same way.
 */
class RedactedExchange internal constructor(
    val pluginId: String,
    /** Method and redacted URL, e.g. `GET https://api.example.com/sites?page=1`. */
    val request: String,
    val requestHeaders: List<Pair<String, String>>,
    /** The HTTP status code, or `failed: <reason>` when no response arrived. */
    val outcome: String,
    val responseBody: String?,
    val durationMillis: Long,
) {
    override fun toString(): String = "[$pluginId] $request -> $outcome (${durationMillis}ms)"
}

/**
 * Receives every plugin request in debug builds. Release builds pass [None], so nothing about requests is
 * recorded at all.
 */
fun interface RequestLogger {
    fun log(exchange: RedactedExchange)

    companion object {
        val None = RequestLogger { }

        /** Prints one line per request to stdout (Logcat on Android). */
        val Console = RequestLogger { println("dache-http $it") }
    }
}
