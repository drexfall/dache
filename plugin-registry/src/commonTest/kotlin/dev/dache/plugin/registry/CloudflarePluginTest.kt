package dev.dache.plugin.registry

import dev.dache.core.account.tokenKey
import dev.dache.core.model.Capability
import dev.dache.core.model.CertificateStatus
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.DeployStatus
import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.provider.DevOpsProvider
import dev.dache.plugin.registry.CloudflareFixtures.ACCOUNT_ID
import dev.dache.plugin.registry.CloudflareFixtures.ZONE_ID
import dev.dache.plugin.runtime.http.createPluginHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
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

    private fun cloudflare(): DevOpsProvider {
        val engine =
            MockEngine { request ->
                requests += request
                val authorized = request.headers[HttpHeaders.Authorization] == "Bearer ${CloudflareFixtures.TOKEN}"
                val path = request.url.encodedPath.removePrefix("/client/v4")
                val (status, body) =
                    when {
                        !authorized -> HttpStatusCode.Forbidden to CloudflareFixtures.UNAUTHORIZED
                        // Second pages of every list are empty.
                        (request.url.parameters["page"] ?: "1") != "1" -> HttpStatusCode.OK to "{\"result\": []}"
                        path == "/accounts" -> HttpStatusCode.OK to CloudflareFixtures.accounts
                        path == "/zones" -> HttpStatusCode.OK to CloudflareFixtures.zones
                        path == "/zones/$ZONE_ID/ssl/certificate_packs" ->
                            HttpStatusCode.OK to CloudflareFixtures.certificatePacks
                        path == "/zones/$ZONE_ID/dns_records" -> HttpStatusCode.OK to CloudflareFixtures.dnsRecords
                        path == "/accounts/$ACCOUNT_ID/pages/projects" ->
                            HttpStatusCode.OK to CloudflareFixtures.pagesProjects
                        path == "/accounts/$ACCOUNT_ID/pages/projects/acme-blog/deployments" ->
                            HttpStatusCode.OK to CloudflareFixtures.blogDeployments
                        path.endsWith("/deployments") -> HttpStatusCode.OK to CloudflareFixtures.noDeployments
                        path == "/accounts/$ACCOUNT_ID/workers/scripts" ->
                            HttpStatusCode.OK to
                                CloudflareFixtures.workers
                        else -> HttpStatusCode.NotFound to """{"success":false,"errors":[{"code":7003}]}"""
                    }
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        return checkNotNull(BundledPlugins.providers(vault, createPluginHttpClient(engine))[ProviderId.CLOUDFLARE])
    }

    private suspend fun snapshot(): ProviderSnapshot = cloudflare().fetchSnapshot(account).getOrThrow()

    @Test
    fun bundledManifestLoads() {
        assertEquals(
            setOf(Capability.DOMAINS, Capability.DNS, Capability.SITES, Capability.DEPLOYMENTS, Capability.WORKERS),
            cloudflare().capabilities,
        )
    }

    @Test
    fun validTokenNamesTheAccount() =
        runTest {
            val info = cloudflare().validateCredentials(CloudflareFixtures.TOKEN).getOrThrow()

            assertEquals("Acme Inc", info.displayName)
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
