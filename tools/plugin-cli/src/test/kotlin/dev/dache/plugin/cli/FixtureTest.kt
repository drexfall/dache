package dev.dache.plugin.cli

import dev.dache.plugin.runtime.http.createPluginHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FixtureTest {
    private val dir = Files.createTempDirectory("dache-fixtures").toFile()
    private val uptimeRobot = File("../../plugin-registry/manifests/uptimerobot.json")

    private fun fixture(
        name: String,
        body: String,
    ) = dir.resolve(name).apply {
        parentFile.mkdirs()
        writeText(body)
    }

    @Test
    fun anExactQueryWinsOverThePath() {
        fixture("api.example.com/v2/items.json", "[]")
        val page2 = fixture("api.example.com/v2/items?page=2&size=10.json", "[]")

        assertEquals(
            page2 to HttpStatusCode.OK,
            Fixtures(dir).find(Url("https://api.example.com/v2/items?size=10&page=2")),
        )
        assertEquals(
            dir.resolve("api.example.com/v2/items.json"),
            Fixtures(dir).find(Url("https://api.example.com/v2/items?page=3"))?.first,
        )
    }

    @Test
    fun aStatusInTheNameIsAnsweredWith() {
        val denied = fixture("api.example.com/v2/secret.403.json", "{}")

        assertEquals(denied to HttpStatusCode.Forbidden, Fixtures(dir).find(Url("https://api.example.com/v2/secret")))
        assertNull(Fixtures(dir).find(Url("https://api.example.com/v2/missing")))
    }

    @Test
    fun testPrintsTheTokenCheckAndTheModels() {
        fixture("api.uptimerobot.com/v3/user/me.json", """{"email": "ops@acme.dev"}""")
        fixture(
            "api.uptimerobot.com/v3/monitors?limit=200.json",
            """{"nextLink": null, "data": [
              {"id": 1, "friendlyName": "Website", "url": "https://acme.dev", "status": "UP"}
            ]}""",
        )
        fixture(
            "api.uptimerobot.com/v3/monitors/1/stats/response-time?includeTimeSeries=true.json",
            """{"summary": {"avg": 120}, "time_series": [{"timestamp": "2026-10-31T23:00:00Z", "value": 120}]}""",
        )

        val outcome =
            runBlocking {
                refresh(
                    uptimeRobot,
                    createPluginHttpClient(Fixtures(dir).engine()),
                    "fixture-token",
                    emptyMap(),
                )
            }

        assertTrue(outcome.ok, outcome.text)
        assertEquals(
            """
            token works for ops@acme.dev
              [x] Monitor statistics (optional)
            monitor (1)
              Website  UP  https://dashboard.uptimerobot.com/monitors/1
            """.trimIndent(),
            outcome.text,
        )
    }

    @Test
    fun aSelfHostedManifestTalksToTheServerSetting() {
        val proxmox = File("../../plugin-registry/manifests/proxmox.json")
        // Recorded under the server's host only, so the refresh passes only if every request went there.
        fixture("pve.lan/api2/json/version.json", """{"data": {"version": "8.2.4"}}""")
        fixture(
            "pve.lan/api2/json/cluster/resources.json",
            """{"data": [{"id": "qemu/100", "name": "web", "node": "pve1", "status": "running"}]}""",
        )
        val client = createPluginHttpClient(Fixtures(dir).engine())

        val outcome = runBlocking { refresh(proxmox, client, "fixture-token", mapOf("server" to "pve.lan:8006")) }

        assertTrue(outcome.ok, outcome.text)
        assertTrue("web  ACTIVE" in outcome.text, outcome.text)
    }

    @Test
    fun aSelfHostedManifestWithoutAServerKeepsThePlaceholderHost() {
        val proxmox = File("../../plugin-registry/manifests/proxmox.json")
        fixture("self.hosted/api2/json/version.json", """{"data": {"version": "8.2.4"}}""")
        fixture("self.hosted/api2/json/cluster/resources.json", """{"data": []}""")
        val client = createPluginHttpClient(Fixtures(dir).engine())

        val outcome = runBlocking { refresh(proxmox, client, "fixture-token", emptyMap()) }

        assertTrue(outcome.ok, outcome.text)
    }

    @Test
    fun aFailingRefreshSaysWhyAndFails() {
        fixture("api.uptimerobot.com/v3/user/me.json", """{"email": "ops@acme.dev"}""")
        fixture("api.uptimerobot.com/v3/monitors.403.json", "{}")

        val outcome =
            runBlocking {
                refresh(
                    uptimeRobot,
                    createPluginHttpClient(Fixtures(dir).engine()),
                    "fixture-token",
                    emptyMap(),
                )
            }

        assertFalse(outcome.ok)
        assertTrue("refresh failed: MissingScope" in outcome.text, outcome.text)
    }

    @Test
    fun anInvalidManifestIsReportedWithoutSendingAnything() {
        val broken = fixture("broken.json", uptimeRobot.readText().replace("\"api.uptimerobot.com\"]", "\"*\"]"))
        var sent = 0
        val engine = MockEngine { error("sent ${++sent} requests") }

        val outcome = runBlocking { refresh(broken, createPluginHttpClient(engine), "fixture-token", emptyMap()) }

        assertFalse(outcome.ok)
        assertTrue("hosts[0]" in outcome.text, outcome.text)
        assertEquals(0, sent)
    }

    @Test
    fun runNeedsTheTokenVariable() {
        val arguments = Arguments(uptimeRobot, "UPTIMEROBOT_TOKEN", emptyMap())

        val outcome = run(arguments) { name -> " ".takeIf { name == "UPTIMEROBOT_TOKEN" } }

        assertFalse(outcome.ok)
        assertEquals("environment variable UPTIMEROBOT_TOKEN is not set", outcome.text)
    }

    @Test
    fun argumentsTakeSettingsInAnyOrder() {
        val parsed = Arguments.parse(listOf("m.json", "--setting", "team=acme", "--fixtures", "dir"), "--fixtures")

        assertEquals("dir", parsed?.option)
        assertEquals(mapOf("team" to "acme"), parsed?.settings)
        assertNull(Arguments.parse(listOf("m.json", "--fixtures"), "--fixtures"))
        assertNull(Arguments.parse(listOf("m.json", "--setting", "team=acme"), "--fixtures"))
        assertNull(Arguments.parse(listOf("m.json", "--fixtures", "d", "--bogus", "x"), "--fixtures"))
    }
}
