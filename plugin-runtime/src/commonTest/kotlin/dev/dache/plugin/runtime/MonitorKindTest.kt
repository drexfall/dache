package dev.dache.plugin.runtime

import dev.dache.core.account.tokenKey
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.MonitorStatus
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
import kotlin.test.assertNull

/** Monitors paged by a next link in the body, with uptime averaged from a histogram. Fake host, fake data. */
class MonitorKindTest {
    private val account = ConnectedAccount(id = "acc-1", providerId = ProviderId.UPTIMEROBOT, displayName = "dev")
    private val vault = MapTokenVault().apply { tokens[account.tokenKey()] = "fake-token-123" }
    private val requests = mutableListOf<HttpRequestData>()

    private val manifest =
        ManifestJson.decode(
            """
            {
              "manifestVersion": 1,
              "id": "example",
              "name": "Example",
              "version": "1.0.0",
              "hosts": ["api.example.com"],
              "auth": { "type": "bearer" },
              "capabilities": ["MONITORS"],
              "validate": { "request": { "url": "https://api.example.com/me" }, "displayName": "email" },
              "resources": [
                {
                  "kind": "monitor",
                  "request": {
                    "url": "https://api.example.com/monitors",
                    "paging": { "type": "link", "next": "nextLink", "sizeParam": "limit", "size": 2, "maxPages": 5 }
                  },
                  "items": "data",
                  "fields": {
                    "nativeId": "id",
                    "name": "friendlyName",
                    "url": "url | default:",
                    "status": "status",
                    "uptimePercent": "lastDayUptimes.histogram.uptime | avg"
                  },
                  "statusMap": { "UP": "UP", "DOWN": "DOWN", "LOOKS_DOWN": "DOWN", "PAUSED": "PAUSED" }
                }
              ]
            }
            """.trimIndent(),
        )

    private suspend fun monitors() =
        ManifestProvider(
            manifest,
            ProviderId.UPTIMEROBOT,
            vault,
            createPluginHttpClient(
                MockEngine { request ->
                    requests += request
                    val body =
                        if (request.url.parameters["cursor"] == null) {
                            """{"nextLink": "https://api.example.com/monitors?limit=2&cursor=2", "data": [
                              {"id": 1, "friendlyName": "Site", "url": "https://acme.dev", "status": "UP",
                               "lastDayUptimes": {"histogram": [{"uptime": 100}, {"uptime": 99}]}},
                              {"id": 2, "friendlyName": "API", "url": "https://api.acme.dev", "status": "LOOKS_DOWN"}
                            ]}"""
                        } else {
                            """{"nextLink": null, "data": [
                              {"id": 3, "friendlyName": "Cron", "url": null, "status": "STARTED"}
                            ]}"""
                        }
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ),
        ).fetchSnapshot(account).getOrThrow().monitors

    @Test
    fun manifestIsValid() {
        assertEquals(emptyList(), ManifestValidator.validate(manifest))
    }

    @Test
    fun followsTheNextLinkInTheBody() =
        runTest {
            val monitors = monitors()

            assertEquals(listOf("Site", "API", "Cron"), monitors.map { it.name })
            assertEquals(2, requests.size)
            assertEquals("2", requests[1].url.parameters["cursor"])
        }

    @Test
    fun mapsStatusAndAveragesUptime() =
        runTest {
            val monitors = monitors()

            assertEquals(
                listOf(MonitorStatus.UP, MonitorStatus.DOWN, MonitorStatus.UNKNOWN),
                monitors.map { it.status },
            )
            assertEquals(99.5, monitors[0].uptimePercent)
            assertNull(monitors[1].uptimePercent)
            assertEquals("", monitors[2].url)
        }
}
