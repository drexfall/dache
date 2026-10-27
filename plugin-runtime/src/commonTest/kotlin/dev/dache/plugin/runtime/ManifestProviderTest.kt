package dev.dache.plugin.runtime

import dev.dache.core.account.tokenKey
import dev.dache.core.model.Capability
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.DeployStatus
import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.core.security.TokenKey
import dev.dache.core.security.TokenVault
import dev.dache.plugin.api.PluginManifest
import dev.dache.plugin.runtime.http.createPluginHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ManifestProviderTest {
    private val account = ConnectedAccount(id = "acc-1", providerId = ProviderId.NETLIFY, displayName = "dev")
    private val vault = MapTokenVault().apply { tokens[account.tokenKey()] = "fake-token-123" }
    private val requests = mutableListOf<HttpRequestData>()

    private fun provider(
        manifest: PluginManifest = TestManifests.sites,
        pages: Map<String, String> = emptyMap(),
        user: String = """{"email":"dev@example.com"}""",
    ) = ManifestProvider(
        manifest = manifest,
        id = ProviderId.NETLIFY,
        vault = vault,
        client =
            createPluginHttpClient(
                MockEngine { request ->
                    requests += request
                    val body =
                        when (request.url.encodedPath) {
                            "/user" -> user
                            "/sites" -> pages[request.url.parameters["page"]] ?: "[]"
                            else -> return@MockEngine respondError(HttpStatusCode.NotFound)
                        }
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ),
    )

    private fun site(
        id: String,
        state: String = "ready",
    ) = """
        {
          "id": "$id",
          "name": "site-$id",
          "admin_url": "https://app.example.com/sites/site-$id",
          "ssl_url": "https://site-$id.example.app",
          "custom_domain": "WWW.Site-$id.com",
          "domain_aliases": ["site-$id.com", "www.site-$id.com"],
          "build_settings": { "repo_url": "https://github.com/acme/site-$id" },
          "published_deploy": {
            "id": "dep-$id",
            "deploy_ssl_url": "https://dep-$id--site-$id.example.app",
            "state": "$state",
            "branch": "main",
            "title": "Fix typo",
            "created_at": "2026-10-27T09:30:00Z",
            "deploy_time": 37
          }
        }
        """.trimIndent()

    @Test
    fun validateUsesDisplayNameExpression() =
        runTest {
            assertEquals("dev@example.com", provider().validateCredentials("fake-token-123").getOrThrow().displayName)
            assertEquals(
                "Example account",
                provider(user = "{}").validateCredentials("fake-token-123").getOrThrow().displayName,
            )
            assertEquals("Bearer fake-token-123", requests.first().headers[HttpHeaders.Authorization])
        }

    @Test
    fun capabilitiesComeFromTheManifest() {
        assertEquals(setOf(Capability.SITES, Capability.DEPLOYMENTS), provider().capabilities)
    }

    @Test
    fun fetchesPagesUntilAShortPageAndMapsSites() =
        runTest {
            val pages = mapOf("1" to "[${site("a")}, ${site("b", "building")}]", "2" to "[${site("c", "weird")}]")

            val sites = provider(pages = pages).fetchSnapshot(account).getOrThrow().sites

            assertEquals(listOf("1", "2"), requests.map { it.url.parameters["page"] })
            assertTrue(requests.all { it.url.parameters["per_page"] == "2" && it.url.parameters["filter"] == "all" })
            assertEquals(listOf("a", "b", "c"), sites.map { it.nativeId })
            val a = sites.first()
            assertEquals("site-a", a.name)
            assertEquals(ProviderId.NETLIFY, a.providerId)
            assertEquals("acc-1", a.accountId)
            assertEquals("https://app.example.com/sites/site-a", a.webUrl)
            assertEquals("https://site-a.example.app", a.primaryUrl)
            assertEquals(listOf("www.site-a.com", "site-a.com"), a.customDomains)
            assertEquals("https://github.com/acme/site-a", a.repoUrl)
            val deploy = a.deployments.single()
            assertEquals(DeployStatus.SUCCESS, deploy.status)
            assertEquals("main", deploy.branch)
            assertEquals("Fix typo", deploy.commitMessage)
            assertEquals(Instant.parse("2026-10-27T09:30:00Z"), deploy.createdAt)
            assertEquals(37, deploy.durationSeconds)
            assertEquals(DeployStatus.BUILDING, sites[1].latestDeployment?.status)
            assertEquals(DeployStatus.UNKNOWN, sites[2].latestDeployment?.status)
        }

    @Test
    fun stopsAtMaxPages() =
        runTest {
            val full = "[${site("a")}, ${site("b")}]"

            provider(pages = (1..10).associate { it.toString() to full }).fetchSnapshot(account).getOrThrow()

            assertEquals(3, requests.size)
        }

    @Test
    fun skipsRecordsMissingRequiredFields() =
        runTest {
            val pages = mapOf("1" to """[${site("a")}, {"id": "no-name"}]""")

            val sites = provider(pages = pages).fetchSnapshot(account).getOrThrow().sites

            assertEquals(listOf("a"), sites.map { it.nativeId })
        }

    @Test
    fun siteWithoutDeployKeepsSiteAndHasNoDeployments() =
        runTest {
            val pages = mapOf("1" to """[{"id":"x","name":"x","admin_url":"https://a","ssl_url":"https://b"}]""")

            val site =
                provider(pages = pages)
                    .fetchSnapshot(account)
                    .getOrThrow()
                    .sites
                    .single()

            assertTrue(site.deployments.isEmpty())
            assertNull(site.repoUrl)
            assertEquals(emptyList(), site.customDomains)
        }

    @Test
    fun missingTokenIsUnauthorizedWithoutARequest() =
        runTest {
            vault.tokens.clear()

            assertIs<ProviderError.Unauthorized>(provider().fetchSnapshot(account).exceptionOrNull())
            assertTrue(requests.isEmpty())
        }

    @Test
    fun nonListResponseIsUnknown() =
        runTest {
            val result = provider(pages = mapOf("1" to """{"sites": []}""")).fetchSnapshot(account)

            assertIs<ProviderError.Unknown>(result.exceptionOrNull())
        }

    private class MapTokenVault : TokenVault {
        val tokens = mutableMapOf<TokenKey, String>()

        override suspend fun put(
            key: TokenKey,
            token: String,
        ) {
            tokens[key] = token
        }

        override suspend fun get(key: TokenKey): String? = tokens[key]

        override suspend fun delete(key: TokenKey) {
            tokens.remove(key)
        }
    }
}
