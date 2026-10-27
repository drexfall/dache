package dev.dache.plugin.runtime.security

import dev.dache.core.model.ProviderError
import dev.dache.plugin.api.AuthSpec
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.takeFrom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AuthInjectorTest {
    private val secret = "fake-token-123"

    private fun request() = HttpRequestBuilder().apply { url.takeFrom("https://api.example.com/sites?page=1") }

    @Test
    fun bearerGoesInAuthorizationOnly() {
        val request = request()

        AuthInjector.inject(request, AuthSpec.Bearer, secret)

        assertEquals("Bearer fake-token-123", request.headers[HttpHeaders.Authorization])
        assertEquals("https://api.example.com/sites?page=1", request.url.buildString())
    }

    @Test
    fun headerUsesDeclaredNameAndPrefix() {
        val request = request()

        AuthInjector.inject(request, AuthSpec.Header("X-Auth", prefix = "Token "), secret)

        assertEquals("Token fake-token-123", request.headers["X-Auth"])
        assertNull(request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun queryReplacesAnyExistingValue() {
        val request = request().apply { url.parameters.append("api_key", "attacker") }

        AuthInjector.inject(request, AuthSpec.Query("api_key"), secret)

        assertEquals(listOf(secret), request.url.parameters.getAll("api_key"))
        assertNull(request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun rejectsSecretsThatCouldSplitHeaders() {
        assertFailsWith<ProviderError.Unauthorized> {
            AuthInjector.inject(request(), AuthSpec.Bearer, "fake\r\nX-Evil: 1")
        }
        assertFailsWith<ProviderError.Unauthorized> { AuthInjector.inject(request(), AuthSpec.Bearer, "") }
    }
}
