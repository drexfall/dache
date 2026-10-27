package dev.dache.plugin.runtime.http

import dev.dache.core.model.ProviderError
import dev.dache.plugin.api.AuthSpec
import dev.dache.plugin.runtime.RedactedExchange
import dev.dache.plugin.runtime.TestManifests
import dev.dache.plugin.runtime.security.HostNotAllowedException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

class PluginHttpTest {
    private val secret = "fake-token-123"
    private val requests = mutableListOf<HttpRequestData>()
    private val logged = mutableListOf<RedactedExchange>()

    private fun http(
        auth: AuthSpec = AuthSpec.Bearer,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = PluginHttp(
        client =
            createPluginHttpClient(
                MockEngine { request ->
                    requests += request
                    handler(request)
                },
            ),
        manifest = TestManifests.sites.copy(auth = auth),
        logger = { logged += it },
    )

    private val url = Url("https://api.example.com/user")

    private fun MockRequestHandleScope.json(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        headers: Map<String, String> = emptyMap(),
    ) = respond(
        body,
        status,
        headersOf(
            *(headers + (HttpHeaders.ContentType to "application/json"))
                .map {
                    it.key to listOf(it.value)
                }.toTypedArray(),
        ),
    )

    @Test
    fun sendsTokenAndHeadersAndParsesJson() =
        runTest {
            val body = http { json("""{"email":"dev@example.com"}""") }.getJson(url, mapOf("X-Extra" to "1"), secret)

            assertEquals(JsonPrimitive("dev@example.com"), body.jsonObject["email"])
            val sent = requests.single()
            assertEquals("Bearer $secret", sent.headers[HttpHeaders.Authorization])
            assertEquals("1", sent.headers["X-Extra"])
            assertEquals("application/json", sent.headers[HttpHeaders.Accept])
        }

    @Test
    fun mapsHttpStatusesToProviderErrors() =
        runTest {
            suspend fun errorFor(
                status: HttpStatusCode,
                headers: Map<String, String> = emptyMap(),
            ) = assertFailsWith<ProviderError> { http { json("{}", status, headers) }.getJson(url, emptyMap(), secret) }

            assertIs<ProviderError.Unauthorized>(errorFor(HttpStatusCode.Unauthorized))
            assertIs<ProviderError.MissingScope>(errorFor(HttpStatusCode.Forbidden))
            assertIs<ProviderError.NotFound>(errorFor(HttpStatusCode.NotFound))
            assertEquals(
                30.seconds,
                assertIs<ProviderError.RateLimited>(
                    errorFor(
                        HttpStatusCode.TooManyRequests,
                        mapOf(
                            "Retry-After" to "30",
                        ),
                    ),
                ).retryAfter,
            )
            assertEquals(503, assertIs<ProviderError.Server>(errorFor(HttpStatusCode.ServiceUnavailable)).code)
        }

    @Test
    fun networkFailureIsNetworkError() =
        runTest {
            assertFailsWith<ProviderError.Network> {
                http { throw IOException("connection reset") }.getJson(url, emptyMap(), secret)
            }
        }

    @Test
    fun nonJsonBodyIsUnknown() =
        runTest {
            val error =
                assertFailsWith<ProviderError.Unknown> { http { json("<html>") }.getJson(url, emptyMap(), secret) }
            assertIs<InvalidResponseException>(error.cause)
        }

    @Test
    fun followsRedirectsOnDeclaredHostsWithTheToken() =
        runTest {
            val http =
                http { request ->
                    if (request.url.encodedPath == "/user") {
                        json("", HttpStatusCode.Found, mapOf(HttpHeaders.Location to "/v2/user"))
                    } else {
                        json("""{"ok":true}""")
                    }
                }

            http.getJson(url, emptyMap(), secret)

            assertEquals(listOf("/user", "/v2/user"), requests.map { it.url.encodedPath })
            assertEquals("Bearer $secret", requests.last().headers[HttpHeaders.Authorization])
        }

    @Test
    fun refusesRedirectsToOtherHostsWithoutSendingAnything() =
        runTest {
            val http =
                http {
                    json(
                        "",
                        HttpStatusCode.Found,
                        mapOf(
                            HttpHeaders.Location to "https://evil.example.net/steal",
                        ),
                    )
                }

            val error = assertFailsWith<ProviderError.Unknown> { http.getJson(url, emptyMap(), secret) }

            assertIs<HostNotAllowedException>(error.cause)
            assertEquals(1, requests.size)
        }

    @Test
    fun refusesHttpDowngradeRedirects() =
        runTest {
            val http =
                http {
                    json(
                        "",
                        HttpStatusCode.MovedPermanently,
                        mapOf(
                            HttpHeaders.Location to "http://api.example.com/user",
                        ),
                    )
                }

            assertFailsWith<ProviderError.Unknown> { http.getJson(url, emptyMap(), secret) }
            assertEquals(1, requests.size)
        }

    @Test
    fun stopsAfterTooManyRedirects() =
        runTest {
            val http = http { json("", HttpStatusCode.Found, mapOf(HttpHeaders.Location to "/user")) }

            assertFailsWith<ProviderError.Unknown> { http.getJson(url, emptyMap(), secret) }
            assertEquals(6, requests.size)
        }

    @Test
    fun loggedExchangesNeverContainTheToken() =
        runTest {
            http(auth = AuthSpec.Query("api_key")) { json("""{"echo":"$secret"}""") }.getJson(url, emptyMap(), secret)

            val entry = logged.single()
            assertEquals(secret, requests.single().url.parameters["api_key"])
            listOf(entry.toString(), entry.requestHeaders.toString(), entry.responseBody.orEmpty()).forEach {
                assertFalse(secret in it, it)
            }
        }
}
