package dev.dache.plugin.runtime.security

import dev.dache.plugin.api.AuthSpec
import io.ktor.http.Headers
import io.ktor.http.Url
import io.ktor.http.decodeURLPart
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Masks secrets before anything about a request is logged: the auth slot, any header or query parameter or
 * JSON key with a sensitive-looking name, and every literal occurrence of the token itself.
 */
internal class Redactor(
    auth: AuthSpec,
    private val secret: String,
) {
    private val sensitiveHeaders =
        SENSITIVE_HEADERS + listOfNotNull((auth as? AuthSpec.Header)?.name?.lowercase())
    private val sensitiveParams = SENSITIVE_NAMES + listOfNotNull((auth as? AuthSpec.Query)?.name?.lowercase())

    fun text(value: String): String = if (secret.isEmpty()) value else value.replace(secret, MASK)

    /** A readable, decoded form of [url] for logs, so an encoded token can't slip past the mask. */
    fun url(url: Url): String {
        val query =
            url.parameters.entries().flatMap { (name, values) ->
                values.map { value -> "$name=" + if (name.lowercase() in sensitiveParams) MASK else text(value) }
            }
        val path = text(url.encodedPath.decodeURLPart())
        val queryString = if (query.isEmpty()) "" else query.joinToString("&", prefix = "?")
        return "${url.protocol.name}://${url.host}$path$queryString"
    }

    fun headers(headers: Headers): List<Pair<String, String>> =
        headers.entries().flatMap { (name, values) ->
            values.map { value -> name to if (name.lowercase() in sensitiveHeaders) MASK else text(value) }
        }

    /** JSON bodies get sensitive keys masked; anything else only has the token itself masked. */
    fun body(body: String): String =
        try {
            redact(Json.parseToJsonElement(body)).toString().let(::text)
        } catch (_: SerializationException) {
            text(body)
        }

    private fun redact(element: JsonElement): JsonElement =
        when (element) {
            is JsonObject ->
                JsonObject(
                    element.mapValues { (key, value) ->
                        if (key.lowercase() in SENSITIVE_NAMES) JsonPrimitive(MASK) else redact(value)
                    },
                )
            is JsonArray -> JsonArray(element.map(::redact))
            else -> element
        }

    companion object {
        const val MASK = "[REDACTED]"

        private val SENSITIVE_HEADERS =
            setOf(
                "authorization",
                "proxy-authorization",
                "cookie",
                "set-cookie",
                "x-api-key",
                "x-auth-key",
                "x-auth-token",
                "api-key",
            )

        private val SENSITIVE_NAMES =
            setOf(
                "token",
                "access_token",
                "refresh_token",
                "api_key",
                "apikey",
                "key",
                "secret",
                "client_secret",
                "password",
                "auth",
                "authorization",
                "signature",
                "private_key",
            )
    }
}
