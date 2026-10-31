package dev.dache.plugin.registry

import dev.dache.core.account.tokenKey
import dev.dache.core.model.ActionRequest
import dev.dache.core.model.ActionTarget
import dev.dache.core.model.Capability
import dev.dache.core.model.CertificateStatus
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.DeployStatus
import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.ServiceStatus
import dev.dache.core.provider.DevOpsProvider
import dev.dache.plugin.registry.CloudflareFixtures.ACCOUNT_ID
import dev.dache.plugin.registry.CloudflareFixtures.ZONE_ID
import dev.dache.plugin.runtime.http.createPluginHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class CloudflarePluginTest {
    private val vault = MapTokenVault()
    private val requests = mutableListOf<HttpRequestData>()

    private val account =
        ConnectedAccount(id = "acc-1", providerId = ProviderId.CLOUDFLARE, displayName = "Acme Inc").also {
            vault.tokens[it.tokenKey()] = CloudflareFixtures.TOKEN
        }

    private val services =
        mapOf(
            "/accounts/$ACCOUNT_ID/r2/buckets" to (HttpStatusCode.OK to R2_BUCKETS),
            "/accounts/$ACCOUNT_ID/cfd_tunnel" to (HttpStatusCode.OK to TUNNELS),
            // The token lacks Queues Read.
            "/accounts/$ACCOUNT_ID/queues" to (HttpStatusCode.Forbidden to CloudflareFixtures.UNAUTHORIZED),
        )

    /** Paths in [failing] answer with that status, as a product the account doesn't use might. */
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    /** Lists the fixtures answer, by path under /client/v4. */
    private val fixed =
        mapOf(
            "/accounts" to CloudflareFixtures.accounts,
            "/zones" to CloudflareFixtures.zones,
            "/zones/$ZONE_ID/ssl/certificate_packs" to CloudflareFixtures.certificatePacks,
            "/zones/$ZONE_ID/dns_records" to CloudflareFixtures.dnsRecords,
            "/accounts/$ACCOUNT_ID/pages/projects" to CloudflareFixtures.pagesProjects,
            "/accounts/$ACCOUNT_ID/pages/projects/acme-blog/deployments" to CloudflareFixtures.blogDeployments,
            "/accounts/$ACCOUNT_ID/workers/scripts" to CloudflareFixtures.workers,
        ).mapValues { (_, body) -> HttpStatusCode.OK to body }

    private fun cloudflare(failing: Map<String, HttpStatusCode> = emptyMap()): DevOpsProvider {
        val routes = fixed + services + failing.mapValues { (_, status) -> status to FAILED }
        val engine =
            MockEngine { request ->
                requests += request
                val authorized = request.headers[HttpHeaders.Authorization] == "Bearer ${CloudflareFixtures.TOKEN}"
                val path = request.url.encodedPath.removePrefix("/client/v4")
                val (status, body) =
                    when {
                        !authorized -> HttpStatusCode.Forbidden to CloudflareFixtures.UNAUTHORIZED
                        request.method != HttpMethod.Get -> HttpStatusCode.OK to """{"success": true, "result": {}}"""
                        // Second pages of every list are empty.
                        (request.url.parameters["page"] ?: "1") != "1" -> HttpStatusCode.OK to "{\"result\": []}"
                        path in routes -> routes.getValue(path)
                        path.endsWith("/deployments") -> HttpStatusCode.OK to CloudflareFixtures.noDeployments
                        else -> HttpStatusCode.NotFound to """{"success":false,"errors":[{"code":7003}]}"""
                    }
                respond(body, status, json)
            }
        return checkNotNull(BundledPlugins.providers(vault, createPluginHttpClient(engine))[ProviderId.CLOUDFLARE])
    }

    private suspend fun snapshot(): ProviderSnapshot = cloudflare().fetchSnapshot(account).getOrThrow()

    @Test
    fun editingADnsRecordPatchesItInItsZone() =
        runTest {
            val provider = cloudflare()
            val record =
                provider
                    .fetchSnapshot(account)
                    .getOrThrow()
                    .dnsRecords
                    .first { it.nativeId == "rec-apex" }
            val target = ActionTarget(record.ref)
            val defaults = provider.actionDefaults(account, ActionRequest("edit-record", target)).getOrThrow()
            assertEquals(mapOf("content" to "203.0.113.10", "ttl" to "300", "proxied" to "false"), defaults)

            val inputs = defaults + ("content" to "203.0.113.20")
            val preview = provider.previewAction(account, ActionRequest("edit-record", target, inputs)).getOrThrow()
            provider.runAction(account, ActionRequest("edit-record", target, inputs)).getOrThrow()

            assertEquals(
                listOf("203.0.113.10" to "203.0.113.20"),
                preview.changes.filter { it.before != it.after }.map {
                    it.before to
                        it.after
                },
            )
            val write = requests.single { it.method == HttpMethod.Patch }
            assertEquals("/client/v4/zones/$ZONE_ID/dns_records/rec-apex", write.url.encodedPath)
            assertEquals(
                """{"content":"203.0.113.20","ttl":300,"proxied":false}""",
                (write.body as TextContent).text.let { Json.parseToJsonElement(it).toString() },
            )
            assertTrue(provider.actions.single { it.id == "delete-record" }.destructive)
        }

    @Test
    fun bundledManifestLoads() {
        assertEquals(
            setOf(
                Capability.DOMAINS,
                Capability.DNS,
                Capability.SITES,
                Capability.DEPLOYMENTS,
                Capability.WORKERS,
                Capability.SERVICES,
            ),
            cloudflare().capabilities,
        )
    }

    @Test
    fun servicesHaveTheirOwnPages() {
        assertEquals(
            listOf("dns", "pages", "workers", "r2", "kv", "d1", "queues", "tunnels"),
            cloudflare().pages.map { it.id },
        )
    }

    @Test
    fun listsR2BucketsAndTunnels() =
        runTest {
            val services = snapshot().services

            val bucket = services.single { it.service == "r2Bucket" }
            assertEquals("acme-assets", bucket.name)
            assertEquals("WEUR", bucket.detail)
            assertEquals("https://dash.cloudflare.com/$ACCOUNT_ID/r2/default/buckets/acme-assets", bucket.webUrl)
            assertEquals(Instant.parse("2026-03-01T10:00:00Z"), bucket.createdAt)
            assertEquals(ServiceStatus.DEGRADED, services.single { it.service == "tunnel" }.status)
        }

    @Test
    fun servicesTheTokenCantReadAreLeftOutAndNamed() =
        runTest {
            val snapshot = snapshot()

            assertTrue(snapshot.services.none { it.service == "queue" })
            assertTrue("Queues Read" in snapshot.missingPermissions, "${snapshot.missingPermissions}")
            assertTrue(snapshot.domains.isNotEmpty())
        }

    @Test
    fun checkingTheTokenListsEveryPermission() =
        runTest {
            val check = cloudflare().checkToken(CloudflareFixtures.TOKEN).getOrThrow()
            val granted = check.permissions.associate { it.permission.name to it.granted }

            assertEquals("Acme Inc", check.displayName)
            assertEquals(true, granted["Account Settings Read"])
            assertEquals(true, granted["DNS Read"])
            assertEquals(true, granted["Workers R2 Storage Read"])
            assertEquals(false, granted["Queues Read"])
            assertTrue(!check.missingRequired)
            assertEquals(
                setOf(
                    "SSL and Certificates Read",
                    "Workers R2 Storage Read",
                    "Workers KV Storage Read",
                    "D1 Read",
                    "Queues Read",
                    "Cloudflare Tunnel Read",
                ),
                check.permissions
                    .filterNot { it.permission.required }
                    .map { it.permission.name }
                    .toSet(),
            )
        }

    @Test
    fun validTokenNamesTheAccount() =
        runTest {
            val info = cloudflare().validateCredentials(CloudflareFixtures.TOKEN).getOrThrow()

            assertEquals("Acme Inc", info.displayName)
        }

    @Test
    fun anInvalidTokenGetsAChecklistSayingSo() =
        runTest {
            // What Cloudflare really answers for a token it doesn't accept: 403 with code 9109.
            val check = cloudflare().checkToken("cf_wrong").getOrThrow()

            assertEquals(null, check.displayName)
            assertEquals(
                "Account Settings Read",
                check.permissions
                    .first()
                    .permission.name,
            )
            assertEquals(false, check.permissions.first().granted)
            assertTrue(check.permissions.drop(1).all { it.granted == null })
        }

    @Test
    fun productsTheAccountDoesntUseNeverFailTheRefresh() =
        runTest {
            val failing =
                mapOf(
                    "/accounts/$ACCOUNT_ID/r2/buckets" to HttpStatusCode.Unauthorized,
                    "/accounts/$ACCOUNT_ID/d1/database" to HttpStatusCode.BadRequest,
                    "/accounts/$ACCOUNT_ID/cfd_tunnel" to HttpStatusCode.InternalServerError,
                )

            val snapshot = cloudflare(failing).fetchSnapshot(account).getOrThrow()

            assertTrue(snapshot.domains.isNotEmpty())
            assertTrue(snapshot.workers.isNotEmpty())
            assertTrue("Workers R2 Storage Read" in snapshot.missingPermissions)
            assertEquals(setOf("D1 Read", "Cloudflare Tunnel Read"), snapshot.unavailable)
        }

    @Test
    fun invalidTokenIsRejected() =
        runTest {
            assertIs<ProviderError.MissingScope>(cloudflare().validateCredentials("cf_wrong").exceptionOrNull())
        }

    @Test
    fun listsZonesWithTheirCertificates() =
        runTest {
            val zone = snapshot().domains.single()

            assertEquals("acme.example", zone.name)
            assertEquals("https://dash.cloudflare.com/$ACCOUNT_ID/acme.example", zone.webUrl)
            assertNull(zone.expiresAt)
            val (universal, advanced) = zone.certificates
            assertEquals(CertificateStatus.ACTIVE, universal.status)
            assertEquals(listOf("acme.example", "*.acme.example"), universal.hosts)
            assertEquals(Instant.parse("2027-01-15T12:00:00Z"), universal.expiresAt)
            assertEquals(CertificateStatus.FAILED, advanced.status)
            assertNull(advanced.expiresAt)
            val packs = requests.single { it.url.encodedPath.endsWith("/certificate_packs") }
            assertEquals("all", packs.url.parameters["status"])
        }

    @Test
    fun listsDnsRecordsPerZone() =
        runTest {
            val records = snapshot().dnsRecords

            assertEquals(listOf("CNAME www.acme.example", "A acme.example"), records.map { it.displayName })
            assertTrue(records.all { it.zoneName == "acme.example" })
            assertEquals("acme-blog.pages.dev", records[0].content)
            assertEquals("https://dash.cloudflare.com/$ACCOUNT_ID/acme.example/dns/records", records[0].webUrl)
            val recordRequest = requests.first { it.url.encodedPath.endsWith("/dns_records") }
            assertEquals("500", recordRequest.url.parameters["per_page"])
        }

    @Test
    fun listsPagesProjectsWithDeployments() =
        runTest {
            val (blog, docs) = snapshot().sites

            assertEquals("acme-blog", blog.name)
            assertEquals("https://acme-blog.pages.dev", blog.primaryUrl)
            assertEquals(listOf("acme-blog.pages.dev", "www.acme.example"), blog.customDomains)
            assertEquals("https://github.com/acme/blog", blog.repoUrl)
            assertEquals("https://dash.cloudflare.com/$ACCOUNT_ID/pages/view/acme-blog", blog.webUrl)

            val latest = checkNotNull(blog.latestDeployment)
            assertEquals(DeployStatus.FAILED, latest.status)
            assertEquals("main", latest.branch)
            assertEquals("Add launch post", latest.commitMessage)
            assertEquals(Instant.parse("2026-10-27T09:30:00.123456Z"), latest.createdAt)
            assertEquals("https://dash.cloudflare.com/$ACCOUNT_ID/pages/view/acme-blog/dep-fake-0002", latest.webUrl)
            assertEquals(DeployStatus.SUCCESS, blog.deployments.single { it.nativeId == "dep-fake-0001" }.status)

            assertNull(docs.repoUrl)
            assertTrue(docs.deployments.isEmpty())
        }

    @Test
    fun listsWorkersWithTheirRoutes() =
        runTest {
            val (api, cron) = snapshot().workers

            assertEquals("acme-api", api.name)
            assertEquals(listOf("api.acme.example/*"), api.routes)
            assertEquals(Instant.parse("2026-10-20T16:45:00Z"), api.modifiedAt)
            assertEquals(
                "https://dash.cloudflare.com/$ACCOUNT_ID/workers/services/view/acme-api/production",
                api.webUrl,
            )
            assertTrue(cron.routes.isEmpty())
        }

    @Test
    fun onlyTalksToCloudflareOverHttps() =
        runTest {
            val cloudflare = cloudflare()

            cloudflare.validateCredentials(CloudflareFixtures.TOKEN)
            cloudflare.fetchSnapshot(account).getOrThrow()

            assertTrue(requests.isNotEmpty())
            assertTrue(requests.all { it.url.protocol == URLProtocol.HTTPS && it.url.host == "api.cloudflare.com" })
        }
}

private const val R2_BUCKETS =
    """{"success": true, "result": {"buckets": [
      {"name": "acme-assets", "creation_date": "2026-03-01T10:00:00.000Z", "location": "WEUR"}
    ]}}"""

private const val TUNNELS =
    """{"success": true, "result": [
      {"id": "t1", "name": "office", "status": "degraded", "created_at": "2026-01-01T00:00:00Z"}
    ]}"""

private const val FAILED = """{"success": false, "errors": [{"code": 10000, "message": "Authentication error"}]}"""
