package dev.dache.plugin.runtime

import dev.dache.core.account.tokenKey
import dev.dache.core.model.AccountSetting
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.plugin.api.ManifestJson
import dev.dache.plugin.runtime.http.createPluginHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AccountSettingsTest {
    private val vault = MapTokenVault()
    private val requests = mutableListOf<HttpRequestData>()

    private val manifest =
        ManifestJson
            .decode(
                """
                {
                  "manifestVersion": 1,
                  "dashboardUrl": "https://dashboard.example.com",
                  "id": "example",
                  "name": "Example",
                  "version": "1.0.0",
                  "hosts": ["api.example.com"],
                  "auth": { "type": "bearer" },
                  "settings": [
                    { "key": "team", "label": "Team slug", "hint": "Leave blank for your personal account" },
                    { "key": "region", "label": "Region", "required": true }
                  ],
                  "capabilities": ["SITES"],
                  "validate": {
                    "request": { "url": "https://api.example.com/{@region}/user", "query": { "slug": "{@team}" } },
                    "displayName": "email"
                  },
                  "resources": [
                    {
                      "kind": "site",
                      "request": { "url": "https://api.example.com/{@region}/projects", "query": { "slug": "{@team}" } },
                      "fields": {
                        "nativeId": "id",
                        "name": "name",
                        "webUrl": "https://app.example.com/{@team}/{name}",
                        "primaryUrl": "https://{domain}"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ).also { assertEquals(emptyList(), ManifestValidator.validate(it)) }

    private val provider =
        ManifestProvider(
            manifest = manifest,
            id = ProviderId.VERCEL,
            vault = vault,
            client =
                createPluginHttpClient(
                    MockEngine { request ->
                        requests += request
                        val body =
                            if (request.url.encodedPath.endsWith("/user")) {
                                """{"email": "dev@example.com"}"""
                            } else {
                                """[{"id": "p1", "name": "docs", "domain": "docs.example.app"}]"""
                            }
                        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                ),
        )

    private fun account(settings: Map<String, String>) =
        ConnectedAccount(id = "acc-1", providerId = ProviderId.VERCEL, displayName = "dev", settings = settings).also {
            vault.tokens[it.tokenKey()] = "fake-token-123"
        }

    @Test
    fun settingsComeFromTheManifest() {
        assertEquals(
            listOf(
                AccountSetting("team", "Team slug", "Leave blank for your personal account", required = false),
                AccountSetting("region", "Region", null, required = true),
            ),
            provider.accountSettings,
        )
    }

    @Test
    fun settingsFillRequestsAndFields() =
        runTest {
            val site =
                provider
                    .fetchSnapshot(account(mapOf("team" to "acme", "region" to "eu")))
                    .getOrThrow()
                    .sites
                    .single()

            val request = requests.single()
            assertEquals("/eu/projects", request.url.encodedPath)
            assertEquals("acme", request.url.parameters["slug"])
            assertEquals("https://app.example.com/acme/docs", site.webUrl)
            assertEquals("https://docs.example.app", site.primaryUrl)
        }

    @Test
    fun aBlankOptionalSettingDropsItsQueryParameterAndFieldsFallBack() =
        runTest {
            val site =
                provider
                    .fetchSnapshot(account(mapOf("region" to "eu")))
                    .getOrThrow()
                    .sites
                    .single()

            assertFalse(
                "slug" in
                    requests
                        .single()
                        .url.parameters
                        .names(),
            )
            assertEquals(ProviderId.VERCEL.dashboardUrl, site.webUrl)
        }

    @Test
    fun validationUsesTheSettings() =
        runTest {
            provider.validateCredentials("fake-token-123", mapOf("team" to "acme", "region" to "us")).getOrThrow()

            assertEquals("/us/user", requests.single().url.encodedPath)
            assertEquals("acme", requests.single().url.parameters["slug"])
        }

    @Test
    fun aMissingRequiredSettingFailsValidationWithoutARequest() =
        runTest {
            val error = provider.validateCredentials("fake-token-123", mapOf("team" to "acme")).exceptionOrNull()

            assertIs<ProviderError.Unknown>(error)
            assertIs<MissingSettingException>(error.cause)
            assertTrue(requests.isEmpty())
        }
}
