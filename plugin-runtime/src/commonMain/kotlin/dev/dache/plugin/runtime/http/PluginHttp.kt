package dev.dache.plugin.runtime.http

import dev.dache.core.model.ProviderError
import dev.dache.plugin.api.PluginManifest
import dev.dache.plugin.runtime.RedactedExchange
import dev.dache.plugin.runtime.RequestLogger
import dev.dache.plugin.runtime.security.AuthInjector
import dev.dache.plugin.runtime.security.HostGuard
import dev.dache.plugin.runtime.security.HostNotAllowedException
import dev.dache.plugin.runtime.security.Redactor
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.takeFrom
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** The provider answered with something that isn't usable. Messages never include response content. */
class InvalidResponseException(
    message: String,
) : Exception(message)

/**
 * Sends one plugin's GET requests. Every hop, redirects included, passes [HostGuard] before it is sent; the
 * token is injected per hop by [AuthInjector]; HTTP and network failures become [ProviderError]s.
 */
internal class PluginHttp(
    private val client: HttpClient,
    private val manifest: PluginManifest,
    private val logger: RequestLogger,
) {
    private val guard = HostGuard(manifest.hosts)

    private class RawResponse(
        val status: Int,
        val headers: Headers,
        val body: String,
    )

    /** @throws ProviderError */
    suspend fun getJson(
        url: Url,
        headers: Map<String, String>,
        secret: String,
    ): JsonElement {
        val redactor = Redactor(manifest.auth, secret)
        var target = url
        repeat(MAX_REDIRECTS + 1) {
            val response = send(target, headers, secret, redactor)
            val location = response.headers[HttpHeaders.Location]
            if (response.status !in REDIRECT_CODES || location == null) return decode(response)
            target = URLBuilder(target).takeFrom(location).build()
        }
        throw ProviderError.Unknown(InvalidResponseException("too many redirects"))
    }

    private suspend fun send(
        target: Url,
        headers: Map<String, String>,
        secret: String,
        redactor: Redactor,
    ): RawResponse {
        checkHost(target)
        val request =
            HttpRequestBuilder().apply {
                method = HttpMethod.Get
                url.takeFrom(target)
                headers.forEach { (name, value) -> this.headers.append(name, value) }
                this.headers[HttpHeaders.Accept] = ContentType.Application.Json.toString()
                this.headers[HttpHeaders.UserAgent] = USER_AGENT
            }
        AuthInjector.inject(request, manifest.auth, secret)
        val started = TimeSource.Monotonic.markNow()
        val response =
            try {
                client.request(request).let { RawResponse(it.status.value, it.headers, it.bodyAsText()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                log(request, redactor, "failed: ${e::class.simpleName}", null, started)
                throw ProviderError.Network()
            }
        log(request, redactor, response.status.toString(), response.body, started)
        return response
    }

    private fun checkHost(target: Url) {
        try {
            guard.check(target)
        } catch (e: HostNotAllowedException) {
            throw ProviderError.Unknown(e)
        }
    }

    private fun decode(response: RawResponse): JsonElement =
        when (response.status) {
            in SUCCESS_CODES ->
                try {
                    Json.parseToJsonElement(response.body)
                } catch (_: SerializationException) {
                    null
                }.takeIf { it is JsonObject || it is JsonArray }
                    ?: throw ProviderError.Unknown(InvalidResponseException("response is not a JSON object or array"))
            UNAUTHORIZED -> throw ProviderError.Unauthorized()
            FORBIDDEN -> throw ProviderError.MissingScope(scope = null)
            NOT_FOUND -> throw ProviderError.NotFound()
            TOO_MANY_REQUESTS ->
                throw ProviderError.RateLimited(response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()?.seconds)
            else -> throw ProviderError.Server(response.status)
        }

    private fun log(
        request: HttpRequestBuilder,
        redactor: Redactor,
        outcome: String,
        body: String?,
        started: TimeSource.Monotonic.ValueTimeMark,
    ) {
        if (logger === RequestLogger.None) return
        logger.log(
            RedactedExchange(
                pluginId = manifest.id,
                request = "${request.method.value} ${redactor.url(request.url.build())}",
                requestHeaders = redactor.headers(request.headers.build()),
                outcome = outcome,
                responseBody = body?.let(redactor::body),
                durationMillis = started.elapsedNow().inWholeMilliseconds,
            ),
        )
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val USER_AGENT = "Dache (+https://github.com/drexfall/dache)"
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val TOO_MANY_REQUESTS = 429
        val SUCCESS_CODES = 200..299
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
