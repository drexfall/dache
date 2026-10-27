package dev.dache.plugin.runtime.security

import dev.dache.plugin.api.AuthSpec
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RedactorTest {
    private val secret = "fake-token-123"

    @Test
    fun masksAuthAndSensitiveHeaders() {
        val redactor = Redactor(AuthSpec.Header("X-Custom-Auth"), secret)

        val headers =
            redactor.headers(
                headersOf(
                    "X-Custom-Auth" to listOf(secret),
                    "Authorization" to listOf("Basic abc"),
                    "Accept" to listOf("application/json"),
                    "X-Echo" to listOf("prefix $secret suffix"),
                ),
            )

        assertEquals(
            listOf(
                "X-Custom-Auth" to "[REDACTED]",
                "Authorization" to "[REDACTED]",
                "Accept" to "application/json",
                "X-Echo" to "prefix [REDACTED] suffix",
            ),
            headers,
        )
    }

    @Test
    fun masksAuthAndSensitiveQueryParametersAndTheSecretInPaths() {
        val redactor = Redactor(AuthSpec.Query("k"), secret)

        val url = redactor.url(Url("https://api.example.com/v1/$secret/sites?k=$secret&token=other&page=2"))

        assertEquals("https://api.example.com/v1/[REDACTED]/sites?k=[REDACTED]&token=[REDACTED]&page=2", url)
    }

    @Test
    fun masksSecretsThatAreUrlEncoded() {
        val redactor = Redactor(AuthSpec.Bearer, "a+b/c=")

        val url = redactor.url(Url("https://api.example.com/v1/a%2Bb%2Fc%3D/x?echo=a%2Bb%2Fc%3D"))

        assertEquals("https://api.example.com/v1/[REDACTED]/x?echo=[REDACTED]", url)
    }

    @Test
    fun masksJsonKeysAndLiteralSecretsInBodies() {
        val redactor = Redactor(AuthSpec.Bearer, secret)

        val body = redactor.body("""{"api_key":"abc","nested":[{"password":"pw","note":"has $secret"}],"n":1}""")

        assertEquals(
            """{"api_key":"[REDACTED]","nested":[{"password":"[REDACTED]","note":"has [REDACTED]"}],"n":1}""",
            body,
        )
    }

    @Test
    fun nonJsonBodiesStillLoseTheSecret() {
        val body = Redactor(AuthSpec.Bearer, secret).body("error: bad token $secret")

        assertFalse(secret in body)
    }
}
