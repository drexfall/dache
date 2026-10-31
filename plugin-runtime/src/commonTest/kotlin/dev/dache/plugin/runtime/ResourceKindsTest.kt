package dev.dache.plugin.runtime

import dev.dache.core.account.tokenKey
import dev.dache.core.model.CertificateStatus
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ProviderSnapshot
import dev.dache.plugin.api.ManifestJson
import dev.dache.plugin.api.PluginManifest
import dev.dache.plugin.runtime.http.createPluginHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** Domains, DNS records and workers, the kinds besides sites. Fake host, fake data. */
class ResourceKindsTest {
    private val account = ConnectedAccount(id = "acc-1", providerId = ProviderId.CLOUDFLARE, displayName = "dev")
    private val vault = MapTokenVault().apply { tokens[account.tokenKey()] = "fake-token-123" }

    private fun manifest(
        capabilities: String,
        resources: String,
    ): PluginManifest =
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
              "capabilities": [$capabilities],
              "validate": { "request": { "url": "https://api.example.com/user" }, "displayName": "email" },
              "resources": [$resources]
            }
            """.trimIndent(),
        )

    private val zonesManifest = zones(certificateStatuses = """{ "ok": "ACTIVE", "late": "EXPIRED" }""")

    private fun zones(certificateStatuses: String) =
        manifest(
            """"DOMAINS", "DNS", "WORKERS"""",
            """
            {
              "kind": "domain",
              "request": { "url": "https://api.example.com/zones" },
              "fields": {
                "nativeId": "id",
                "name": "name",
                "webUrl": "https://dash.example.com/{name}",
                "certificates": {
                  "from": "certs",
                  "fields": { "nativeId": "id", "status": "state", "hosts": "hosts", "expiresAt": "expires | date" },
                  "statusMap": $certificateStatuses
                }
              }
            },
            {
              "kind": "dnsRecord",
              "forEach": { "request": { "url": "https://api.example.com/zones" } },
              "request": { "url": "https://api.example.com/zones/{id}/records" },
              "fields": { "nativeId": "id", "zoneName": "^.name", "type": "type", "name": "name", "content": "content" }
            },
            {
              "kind": "worker",
              "request": { "url": "https://api.example.com/scripts" },
              "fields": { "nativeId": "id", "name": "id", "routes": "routes.pattern", "modifiedAt": "modified | date" }
            }
            """,
        )

    private suspend fun snapshot(manifest: PluginManifest): ProviderSnapshot {
        val engine =
            MockEngine { request ->
                val body =
                    when (request.url.encodedPath) {
                        "/zones" ->
                            """
                            [{"id": "z1", "name": "example.dev", "certs": [
                              {"id": "c1", "state": "ok", "hosts": ["example.dev", "*.example.dev"],
                               "expires": "2027-01-01T00:00:00Z"},
                              {"id": "c2", "state": "weird", "hosts": []}
                            ]}]
                            """.trimIndent()
                        "/zones/z1/records" ->
                            """[{"id": "r1", "type": "CNAME", "name": "www.example.dev", "content": "x.example.app"}]"""
                        "/scripts" ->
                            """[{"id": "api", "routes": [{"pattern": "api.example.dev/*"}], "modified": "2026-10-01T00:00:00Z"},
                               {"id": "cron"}]"""
                        else -> "[]"
                    }
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        return ManifestProvider(manifest, ProviderId.CLOUDFLARE, vault, createPluginHttpClient(engine))
            .fetchSnapshot(account)
            .getOrThrow()
    }

    @Test
    fun manifestIsValid() {
        assertEquals(emptyList(), ManifestValidator.validate(zonesManifest))
    }

    @Test
    fun mapsDomainsWithCertificates() =
        runTest {
            val domain = snapshot(zonesManifest).domains.single()

            assertEquals("example.dev", domain.name)
            assertEquals("https://dash.example.com/example.dev", domain.webUrl)
            assertNull(domain.expiresAt)
            assertEquals(
                listOf(CertificateStatus.ACTIVE, CertificateStatus.UNKNOWN),
                domain.certificates.map { it.status },
            )
            assertEquals(listOf("example.dev", "*.example.dev"), domain.certificates[0].hosts)
            assertEquals(Instant.parse("2027-01-01T00:00:00Z"), domain.certificates[0].expiresAt)
        }

    @Test
    fun mapsDnsRecordsWithTheirZoneFromTheParent() =
        runTest {
            val record = snapshot(zonesManifest).dnsRecords.single()

            assertEquals("example.dev", record.zoneName)
            assertEquals("CNAME www.example.dev", record.displayName)
            assertEquals(ProviderId.CLOUDFLARE.dashboardUrl, record.webUrl)
        }

    @Test
    fun mapsWorkers() =
        runTest {
            val workers = snapshot(zonesManifest).workers

            assertEquals(listOf("api", "cron"), workers.map { it.name })
            assertEquals(listOf("api.example.dev/*"), workers[0].routes)
            assertEquals(Instant.parse("2026-10-01T00:00:00Z"), workers[0].modifiedAt)
            assertEquals(emptyList(), workers[1].routes)
            assertNull(workers[1].modifiedAt)
        }

    @Test
    fun statusMapsOnlyTakeTheFieldsOwnStatuses() {
        val manifest = zones(certificateStatuses = """{ "ok": "SUCCESS" }""")

        val problems = ManifestValidator.validate(manifest).map { it.toString() }

        assertEquals(1, problems.size, "$problems")
        assertEquals(
            "resources[0].fields.certificates.statusMap.ok: unknown status \"SUCCESS\"; " +
                "use one of ACTIVE, PENDING, EXPIRED, FAILED, INACTIVE, UNKNOWN",
            problems.single(),
        )
    }
}
