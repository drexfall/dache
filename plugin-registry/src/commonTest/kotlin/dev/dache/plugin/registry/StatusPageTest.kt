package dev.dache.plugin.registry

import dev.dache.core.model.ProviderId
import dev.dache.core.model.StatusLevel
import dev.dache.plugin.runtime.http.createPluginHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatusPageTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val recording = Mutex()

    // The shape of Statuspage's /api/v2/summary.json, trimmed.
    private val summary =
        """
        {
          "page": { "url": "https://www.cloudflarestatus.com" },
          "status": { "indicator": "major", "description": "Partial System Outage" },
          "components": [
            { "id": "eu", "name": "Europe", "status": "degraded_performance", "group": true, "group_id": null },
            { "id": "fra", "name": "Frankfurt, Germany - (FRA)", "status": "under_maintenance", "group_id": "eu" },
            { "id": "led", "name": "Saint Petersburg, Russia - (LED)", "status": "major_outage", "group_id": "eu" },
            { "id": "api", "name": "API", "status": "operational", "group_id": null }
          ],
          "incidents": [
            { "name": "Elevated errors in LED", "status": "investigating", "impact": "major",
              "updated_at": "2026-11-01T01:50:00.000Z", "shortlink": "https://stspg.io/abc" }
          ]
        }
        """.trimIndent()

    @Test
    fun cloudflaresStatusPageReadsWithoutTheToken() =
        runTest {
            val engine =
                MockEngine { request ->
                    // Recording must not race: fan-outs send four requests at a time.
                    recording.withLock { requests += request }
                    respond(summary, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val cloudflare =
                BundledPlugins
                    .providers(
                        MapTokenVault(),
                        createPluginHttpClient(engine),
                    ).getValue(ProviderId.CLOUDFLARE)

            val status = cloudflare.fetchStatus().getOrThrow()

            assertEquals("www.cloudflarestatus.com", requests.single().url.host)
            assertNull(requests.single().headers[HttpHeaders.Authorization])
            assertEquals(StatusLevel.PARTIAL_OUTAGE, status.level)
            assertEquals("Partial System Outage", status.description)
            assertEquals(
                listOf(
                    Triple("Frankfurt, Germany - (FRA)", StatusLevel.MAINTENANCE, "Europe"),
                    Triple("Saint Petersburg, Russia - (LED)", StatusLevel.MAJOR_OUTAGE, "Europe"),
                    Triple("API", StatusLevel.OPERATIONAL, null),
                ),
                status.components.map { Triple(it.name, it.level, it.group) },
            )
            val incident = status.incidents.single()
            assertEquals(StatusLevel.PARTIAL_OUTAGE, incident.impact)
            assertTrue(incident.updatedAt != null)
        }

    @Test
    fun everyStatuspageProviderHasAStatusSection() {
        val withStatus =
            BundledPlugins.manifests
                .filter { it.status != null }
                .map { it.id }
                .toSet()

        assertEquals(setOf("cloudflare", "github", "netlify", "vercel", "digitalocean"), withStatus)
    }
}
