package dev.dache.plugin.registry

import dev.dache.core.account.tokenKey
import dev.dache.core.model.Capability
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.MonitorStatus
import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.core.provider.DevOpsProvider
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
import kotlin.test.assertIs

/** Response shapes follow UptimeRobot's v3 OpenAPI spec (MonitorPaginationDto, MonitorDto, UserDto). */
class UptimeRobotPluginTest {
    private val vault = MapTokenVault()
    private val requests = mutableListOf<HttpRequestData>()

    private val account =
        ConnectedAccount(id = "acc-1", providerId = ProviderId.UPTIMEROBOT, displayName = "ops@acme.dev").also {
            vault.tokens[it.tokenKey()] = TOKEN
        }

    private fun uptimeRobot(): DevOpsProvider {
        val engine =
            MockEngine { request ->
                requests += request
                val (status, body) =
                    when {
                        request.headers[HttpHeaders.Authorization] != "Bearer $TOKEN" ->
                            HttpStatusCode.Unauthorized to """{"message":"Unauthorized"}"""
                        request.url.encodedPath == "/v3/user/me" ->
                            HttpStatusCode.OK to """{"email": "ops@acme.dev", "fullName": "Ops", "monitorsCount": 3}"""
                        request.url.encodedPath != "/v3/monitors" -> HttpStatusCode.NotFound to "{}"
                        request.url.parameters["cursor"] == null -> HttpStatusCode.OK to PAGE_1
                        else -> HttpStatusCode.OK to PAGE_2
                    }
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        return checkNotNull(BundledPlugins.providers(vault, createPluginHttpClient(engine))[ProviderId.UPTIMEROBOT])
    }

    @Test
    fun bundledManifestLoads() {
        val provider = uptimeRobot()

        assertEquals(setOf(Capability.MONITORS), provider.capabilities)
        assertEquals(listOf("monitors"), provider.pages.map { it.id })
    }

    @Test
    fun validKeyNamesTheAccountByEmail() =
        runTest {
            assertEquals("ops@acme.dev", uptimeRobot().validateCredentials(TOKEN).getOrThrow().displayName)
        }

    @Test
    fun invalidKeyIsUnauthorized() =
        runTest {
            assertIs<ProviderError.Unauthorized>(uptimeRobot().validateCredentials("nope").exceptionOrNull())
        }

    @Test
    fun listsMonitorsAcrossPages() =
        runTest {
            val monitors = uptimeRobot().fetchSnapshot(account).getOrThrow().monitors

            assertEquals(listOf("Website", "API", "Nightly job"), monitors.map { it.name })
            assertEquals(
                listOf(MonitorStatus.UP, MonitorStatus.DOWN, MonitorStatus.PAUSED),
                monitors.map { it.status },
            )
            assertEquals("200", requests.first { it.url.encodedPath == "/v3/monitors" }.url.parameters["limit"])
        }

    @Test
    fun uptimeIsTheLastDaysAverage() =
        runTest {
            val website =
                uptimeRobot()
                    .fetchSnapshot(account)
                    .getOrThrow()
                    .monitors
                    .first()

            assertEquals(99.0, website.uptimePercent)
            assertEquals("https://acme.dev", website.url)
            assertEquals("https://dashboard.uptimerobot.com/monitors/800001", website.webUrl)
        }

    @Test
    fun aHeartbeatWithoutAUrlStillShows() =
        runTest {
            val heartbeat =
                uptimeRobot()
                    .fetchSnapshot(account)
                    .getOrThrow()
                    .monitors
                    .last()

            assertEquals("", heartbeat.url)
        }

    private companion object {
        const val TOKEN = "ur_fake_key_for_tests_only"

        const val PAGE_1 = """{
          "nextLink": "https://api.uptimerobot.com/v3/monitors?limit=200&cursor=800002",
          "data": [
            {"id": 800001, "friendlyName": "Website", "url": "https://acme.dev", "status": "UP", "type": "HTTP",
             "lastDayUptimes": {"bucketSize": 3600, "histogram": [{"timestamp": 1, "uptime": 100},
                                                                  {"timestamp": 2, "uptime": 98}]}},
            {"id": 800002, "friendlyName": "API", "url": "https://api.acme.dev/health", "status": "LOOKS_DOWN",
             "type": "KEYWORD"}
          ]
        }"""

        const val PAGE_2 = """{
          "nextLink": null,
          "data": [
            {"id": 800003, "friendlyName": "Nightly job", "url": null, "status": "PAUSED", "type": "HEARTBEAT"}
          ]
        }"""
    }
}
