package dev.dache.plugin.runtime

import dev.dache.core.account.tokenKey
import dev.dache.core.model.Billing
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ServerStatus
import dev.dache.plugin.api.ManifestJson
import dev.dache.plugin.api.PageSpec
import dev.dache.plugin.runtime.http.createPluginHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Servers, and billing read from a single object. Fake host, fake data. */
class ServerAndBillingKindsTest {
    private val account = ConnectedAccount(id = "acc-1", providerId = ProviderId.DIGITALOCEAN, displayName = "dev")
    private val vault = MapTokenVault().apply { tokens[account.tokenKey()] = "fake-token-123" }

    private val manifest =
        ManifestJson.decode(
            """
            {
              "manifestVersion": 1,
              "dashboardUrl": "https://dashboard.example.com",
              "id": "example",
              "name": "Example",
              "version": "1.0.0",
              "hosts": ["api.example.com"],
              "auth": { "type": "bearer" },
              "capabilities": ["SERVERS", "BILLING"],
              "validate": { "request": { "url": "https://api.example.com/account" }, "displayName": "email" },
              "resources": [
                {
                  "kind": "server",
                  "request": { "url": "https://api.example.com/droplets" },
                  "items": "droplets",
                  "fields": {
                    "nativeId": "id",
                    "name": "name",
                    "region": "region.slug",
                    "status": "status",
                    "publicIpv4": "networks.v4.type=public.0.ip_address"
                  },
                  "statusMap": { "active": "ACTIVE", "off": "OFF" }
                },
                {
                  "kind": "billing",
                  "request": { "url": "https://api.example.com/balance" },
                  "items": "$",
                  "fields": {
                    "currency": "currency | default:USD",
                    "monthToDateUsage": "month_to_date_usage",
                    "accountBalance": "account_balance"
                  }
                }
              ]
            }
            """.trimIndent(),
        )

    private suspend fun snapshot() =
        ManifestProvider(
            manifest,
            ProviderId.DIGITALOCEAN,
            vault,
            createPluginHttpClient(
                MockEngine { request ->
                    val body =
                        when (request.url.encodedPath) {
                            "/droplets" ->
                                """{"droplets": [
                                  {"id": 1, "name": "api-1", "status": "active", "region": {"slug": "fra1"},
                                   "networks": {"v4": [{"ip_address": "10.0.0.2", "type": "private"},
                                                       {"ip_address": "203.0.113.7", "type": "public"}]}},
                                  {"id": 2, "name": "old", "status": "archive", "region": {"slug": "nyc3"},
                                   "networks": {"v4": []}}
                                ]}"""
                            "/balance" -> """{"month_to_date_usage": "23.45", "account_balance": "-5.00"}"""
                            else -> "[]"
                        }
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ),
        ).fetchSnapshot(account).getOrThrow()

    @Test
    fun manifestIsValid() {
        assertEquals(emptyList(), ManifestValidator.validate(manifest))
    }

    @Test
    fun mapsServersWithTheirPublicAddress() =
        runTest {
            val servers = snapshot().servers

            assertEquals(listOf("api-1", "old"), servers.map { it.name })
            assertEquals(listOf("203.0.113.7", null), servers.map { it.publicIpv4 })
            assertEquals(listOf(ServerStatus.ACTIVE, ServerStatus.UNKNOWN), servers.map { it.status })
            assertEquals("fra1", servers[0].region)
        }

    @Test
    fun readsBillingFromOneObject() =
        runTest {
            assertEquals(Billing("USD", 23.45, -5.0, emptyList()), snapshot().billing)
        }

    @Test
    fun billingIsOnNoPage() {
        assertEquals(listOf("server"), manifestPages(manifest).map { it.id })
        val onAPage = manifest.copy(pages = listOf(PageSpec("all", "All", listOf("server", "billing"))))
        assertTrue(
            ManifestValidator.validate(onAPage).any { "\"billing\" is not a kind or service id" in it.message },
        )
        assertEquals(
            emptyList(),
            ManifestValidator.validate(manifest.copy(pages = listOf(PageSpec("servers", "Servers", listOf("server"))))),
        )
    }
}
