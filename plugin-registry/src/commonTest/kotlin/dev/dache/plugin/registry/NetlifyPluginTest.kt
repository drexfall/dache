package dev.dache.plugin.registry

import dev.dache.core.account.tokenKey
import dev.dache.core.model.Capability
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.DeployStatus
import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.core.provider.DevOpsProvider
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

class NetlifyPluginTest {
    private val account = ConnectedAccount(id = "acc-1", providerId = ProviderId.NETLIFY, displayName = "dev")
    private val vault = MapTokenVault()
    private val requests = mutableListOf<HttpRequestData>()

    private fun netlify(): DevOpsProvider {
        val engine =
            MockEngine { request ->
                requests += request
                val authorized = request.headers[HttpHeaders.Authorization] == "Bearer ${NetlifyFixtures.TOKEN}"
                val (status, body) =
                    when {
                        !authorized -> HttpStatusCode.Unauthorized to NetlifyFixtures.UNAUTHORIZED
                        request.url.encodedPath == "/api/v1/user" -> HttpStatusCode.OK to NetlifyFixtures.user
                        request.url.encodedPath == "/api/v1/sites" && request.url.parameters["page"] == "1" ->
                            HttpStatusCode.OK to NetlifyFixtures.sitesPage1
                        else -> HttpStatusCode.NotFound to """{"code":404,"message":"Not Found"}"""
                    }
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        val providers = BundledPlugins.providers(vault, createPluginHttpClient(engine))
        return checkNotNull(providers[ProviderId.NETLIFY])
    }

    @Test
    fun bundledManifestLoads() {
        val manifest = BundledPlugins.manifests.single { it.id == "netlify" }

        assertEquals(listOf("api.netlify.com"), manifest.hosts)
        assertEquals(
            setOf(Capability.SITES, Capability.DEPLOYMENTS, Capability.DOMAINS, Capability.DNS, Capability.SERVICES),
            netlify().capabilities,
        )
    }

    @Test
    fun validTokenNamesTheAccountByEmail() =
        runTest {
            val info = netlify().validateCredentials(NetlifyFixtures.TOKEN).getOrThrow()

            assertEquals("dev@example.com", info.displayName)
            assertEquals("https://api.netlify.com/api/v1/user", requests.single().url.toString())
        }

    @Test
    fun invalidTokenIsUnauthorized() =
        runTest {
            val result = netlify().validateCredentials("nfp_wrong_fake_token")

            assertIs<ProviderError.Unauthorized>(result.exceptionOrNull())
        }

    @Test
    fun listsSitesWithTheirLatestDeploy() =
        runTest {
            vault.tokens[account.tokenKey()] = NetlifyFixtures.TOKEN

            val sites = netlify().fetchSnapshot(account).getOrThrow().sites

            // Sites come first; DNS zones and forms follow.
            val request = requests.first()
            assertEquals("/api/v1/sites", request.url.encodedPath)
            assertEquals("all", request.url.parameters["filter"])
            assertEquals("100", request.url.parameters["per_page"])

            assertEquals(listOf("acme-docs", "acme-blog"), sites.map { it.name })
            val docs = sites[0]
            assertEquals("2f9c6a1e-0000-4000-8000-00000000d0c5", docs.nativeId)
            assertEquals("https://app.netlify.com/sites/acme-docs", docs.webUrl)
            assertEquals("https://docs.acme.example", docs.primaryUrl)
            assertEquals(listOf("docs.acme.example", "www.docs.acme.example"), docs.customDomains)
            assertEquals("https://github.com/acme/docs", docs.repoUrl)
            val deploy = checkNotNull(docs.latestDeployment)
            assertEquals(DeployStatus.SUCCESS, deploy.status)
            assertEquals("main", deploy.branch)
            assertEquals("Update install guide", deploy.commitMessage)
            assertEquals(Instant.parse("2026-10-27T08:15:02.531Z"), deploy.createdAt)
            assertEquals(94, deploy.durationSeconds)

            val blog = sites[1]
            assertTrue(blog.customDomains.isEmpty())
            assertNull(blog.repoUrl)
            assertNull(blog.latestDeployment)
        }

    @Test
    fun onlyTalksToNetlifyOverHttps() =
        runTest {
            vault.tokens[account.tokenKey()] = NetlifyFixtures.TOKEN
            val netlify = netlify()

            netlify.validateCredentials(NetlifyFixtures.TOKEN)
            netlify.fetchSnapshot(account)

            assertTrue(requests.isNotEmpty())
            assertTrue(requests.all { it.url.protocol == URLProtocol.HTTPS && it.url.host == "api.netlify.com" })
        }
}
