package dev.dache.plugin.runtime.http

import dev.dache.core.model.ProviderError
import dev.dache.plugin.api.ActionSpec
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
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.http.takeFrom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.time.TimeSource

/** The provider answered with something that isn't usable. Messages never include response content. */
class InvalidResponseException(
    message: String,
) : Exception(message)

/** A successful response: its JSON body, and headers for paging. */
internal class JsonResponse(
    val body: JsonElement,
    val headers: Headers,
)

/**
 * Sends one plugin's GET requests. Every hop, redirects included, passes [HostGuard] before it is sent; the
 * token is injected per hop by [AuthInjector]; HTTP and network failures become [ProviderError]s.
 */
internal class PluginHttp(
    private val client: HttpClient,
    private val manifest: PluginManifest,
    private val logger: RequestLogger,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
    port: Int = URLProtocol.HTTPS.defaultPort,
) {
    private val guard = HostGuard(manifest.hosts, port)
    private val limitersLock = Mutex()

    // Keyed by a hash so tokens aren't kept around as map keys; each account's limit is its own.
    private val limiters = mutableMapOf<Int, RateLimiter>()

    private suspend fun limiter(secret: String): RateLimiter =
        limitersLock.withLock {
            limiters.getOrPut(secret.hashCode()) { RateLimiter(manifest.limits.requestsPerMinute, timeSource) }
        }

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
    ): JsonElement = get(url, headers, secret).body

    /** @throws ProviderError */
    suspend fun get(
        url: Url,
        headers: Map<String, String>,
        secret: String,
    ): JsonResponse {
        val redactor = Redactor(manifest.auth, secret)
        val limiter = limiter(secret)
        var target = url
        repeat(MAX_REDIRECTS + 1) {
            val response = sendPaced(target, headers, secret, redactor, limiter)
            val location = response.headers[HttpHeaders.Location]
            if (response.status !in REDIRECT_CODES || location == null) return decode(response)
            target = target.resolve(location)
        }
        throw ProviderError.Unknown(InvalidResponseException("too many redirects"))
    }

    /**
     * Sends [action]'s write. Only an action declared in the manifest may send anything but GET, and a write
     * never follows a redirect, so its body can't be replayed to another URL.
     *
     * @throws ProviderError
     */
    suspend fun execute(
        action: ActionSpec,
        url: Url,
        body: JsonObject?,
        secret: String,
    ) {
        if (manifest.actions.none { it === action || it == action }) {
            throw ProviderError.Unknown(IllegalStateException("${action.id} is not an action of ${manifest.id}"))
        }
        val method = HttpMethod.parse(action.request.method)
        val limiter = limiter(secret)
        takeFromBudget()
        limiter.acquire()
        val response = send(url, emptyMap(), secret, Redactor(manifest.auth, secret), method, body)
        waitFrom(response.headers)?.let { limiter.block(it) }
        when (response.status) {
            in SUCCESS_CODES -> Unit
            in REDIRECT_CODES -> throw ProviderError.Unknown(InvalidResponseException("a write was redirected"))
            else -> decode(response)
        }
    }

    /**
     * Sends through [limiter]. A 429 that asks for a short wait is waited out and retried; a response saying no
     * requests are left holds back the next ones until the reset.
     */
    private suspend fun sendPaced(
        target: Url,
        headers: Map<String, String>,
        secret: String,
        redactor: Redactor,
        limiter: RateLimiter,
    ): RawResponse {
        repeat(MAX_RATE_LIMIT_RETRIES) {
            takeFromBudget()
            limiter.acquire()
            val response = send(target, headers, secret, redactor)
            val wait = waitFrom(response.headers)
            if (wait != null) limiter.block(wait)
            if (response.status != TOO_MANY_REQUESTS || wait == null || wait > RateLimiter.MAX_WAIT) return response
        }
        takeFromBudget()
        limiter.acquire()
        return send(target, headers, secret, redactor)
    }

    private suspend fun send(
        target: Url,
        headers: Map<String, String>,
        secret: String,
        redactor: Redactor,
        method: HttpMethod = HttpMethod.Get,
        body: JsonObject? = null,
    ): RawResponse {
        checkHost(target)
        val request =
            HttpRequestBuilder().apply {
                this.method = method
                body?.let { setBody(TextContent(it.toString(), ContentType.Application.Json)) }
                url.takeFrom(target)
                headers.forEach { (name, value) -> this.headers.append(name, value) }
                // A manifest may ask for a vendor JSON type, such as GitHub's application/vnd.github+json.
                if (HttpHeaders.Accept !in this.headers) {
                    this.headers[HttpHeaders.Accept] = ContentType.Application.Json.toString()
                }
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

    private fun decode(response: RawResponse): JsonResponse =
        when (response.status) {
            in SUCCESS_CODES ->
                try {
                    Json.parseToJsonElement(response.body)
                } catch (_: SerializationException) {
                    null
                }.takeIf { it is JsonObject || it is JsonArray }
                    ?.let { JsonResponse(it, response.headers) }
                    ?: throw ProviderError.Unknown(InvalidResponseException("response is not a JSON object or array"))
            UNAUTHORIZED -> throw ProviderError.Unauthorized()
            FORBIDDEN -> throw ProviderError.MissingScope(scope = null)
            NOT_FOUND -> throw ProviderError.NotFound()
            TOO_MANY_REQUESTS -> throw ProviderError.RateLimited(waitFrom(response.headers))
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
        const val MAX_RATE_LIMIT_RETRIES = 2
        const val USER_AGENT = "Dache (+https://github.com/drexfall/dache)"
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val TOO_MANY_REQUESTS = 429
        val SUCCESS_CODES = 200..299
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}

/**
 * [reference] (absolute, or relative like `/sites?page=2`) resolved against this URL. Unlike a bare
 * `URLBuilder.takeFrom`, the base URL's query isn't carried over, except for a fragment-only reference.
 *
 * @throws io.ktor.http.URLParserException
 */
internal fun Url.resolve(reference: String): Url =
    URLBuilder(this)
        .apply {
            if (!reference.startsWith("#")) parameters.clear()
            fragment = ""
            takeFrom(reference)
        }.build()
