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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class VercelPluginTest {
    private val vault = MapTokenVault()
    private val requests = mutableListOf<HttpRequestData>()
    private val recording = Mutex()

    private fun account(settings: Map<String, String> = emptyMap()) =
        ConnectedAccount(id = "acc-1", providerId = ProviderId.VERCEL, displayName = "dev", settings = settings).also {
            vault.tokens[it.tokenKey()] = VercelFixtures.TOKEN
        }

    private fun vercel(): DevOpsProvider {
        val engine =
            MockEngine { request ->
                // Recording must not race: fan-outs send four requests at a time.
                recording.withLock { requests += request }
                val authorized = request.headers[HttpHeaders.Authorization] == "Bearer ${VercelFixtures.TOKEN}"
                val params = request.url.parameters
                val (status, body) =
                    when {
                        !authorized -> HttpStatusCode.Forbidden to VercelFixtures.UNAUTHORIZED
                        request.url.encodedPath == "/v2/user" -> HttpStatusCode.OK to VercelFixtures.user
                        request.url.encodedPath == "/v10/projects" && params["until"] == null ->
                            HttpStatusCode.OK to VercelFixtures.projectsPage1
                        request.url.encodedPath == "/v10/projects" && params["until"] == "1793000000000" ->
                            HttpStatusCode.OK to VercelFixtures.projectsPage2
                        request.url.encodedPath == "/v7/deployments" &&
                            params["projectId"] == "prj_fakeDocs00000000000001" ->
                            HttpStatusCode.OK to VercelFixtures.docsDeployments
                        request.url.encodedPath == "/v7/deployments" ->
                            HttpStatusCode.OK to
                                VercelFixtures.NO_DEPLOYMENTS
                        else -> HttpStatusCode.NotFound to """{"error":{"code":"not_found"}}"""
                    }
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        return checkNotNull(BundledPlugins.providers(vault, createPluginHttpClient(engine))[ProviderId.VERCEL])
    }

    @Test
    fun bundledManifestLoads() {
        val vercel = vercel()

        assertEquals(
            setOf(Capability.SITES, Capability.DEPLOYMENTS, Capability.DOMAINS, Capability.DNS, Capability.SERVICES),
            vercel.capabilities,
        )
        assertEquals(listOf("team"), vercel.accountSettings.map { it.key })
        assertFalse(vercel.accountSettings.single().required)
    }

    @Test
    fun writesThatReplaceLiveValuesAreDestructive() {
        val destructive =
            vercel()
                .actions
                .filter { it.destructive }
                .map { it.id }
                .toSet()

        // Overwriting an environment variable is as hard to undo as deleting it: the old value isn't kept.
        assertEquals(setOf("rollback", "edit-env", "delete-env"), destructive)
    }

    @Test
    fun validTokenNamesTheAccountByEmail() =
        runTest {
            assertEquals("dev@example.com", vercel().validateCredentials(VercelFixtures.TOKEN).getOrThrow().displayName)
        }

    @Test
    fun invalidTokenIsRejected() =
        runTest {
            val result = vercel().validateCredentials("vercel_wrong_fake_token")

            assertIs<ProviderError.MissingScope>(result.exceptionOrNull())
        }

    @Test
    fun listsProjectsAcrossPagesWithRecentDeployments() =
        runTest {
            val sites = vercel().fetchSnapshot(account()).getOrThrow().sites

            // Sites and environment variables each page through the projects.
            val projectRequests = requests.filter { it.url.encodedPath == "/v10/projects" }
            assertEquals(
                listOf(null, "1793000000000", null, "1793000000000"),
                projectRequests.map { it.url.parameters["until"] },
            )
            assertTrue(projectRequests.all { it.url.parameters["limit"] == "100" })
            assertEquals(listOf("acme-docs", "acme-blog"), sites.map { it.name })

            val docs = sites[0]
            assertEquals("prj_fakeDocs00000000000001", docs.nativeId)
            assertEquals("https://docs.acme.example", docs.primaryUrl)
            assertEquals(listOf("docs.acme.example", "acme-docs.vercel.app"), docs.customDomains)
            assertEquals("https://github.com/acme/docs", docs.repoUrl)
            assertEquals(ProviderId.VERCEL.dashboardUrl, docs.webUrl)

            assertEquals(2, docs.deployments.size)
            val latest = checkNotNull(docs.latestDeployment)
            assertEquals("dpl_fakeDeploy000000000002", latest.nativeId)
            assertEquals(DeployStatus.BUILDING, latest.status)
            assertEquals("main", latest.branch)
            assertEquals("Add search page", latest.commitMessage)
            assertEquals(Instant.parse("2026-10-27T09:30:00.120Z"), latest.createdAt)
            assertEquals("https://vercel.com/acme/acme-docs/fakeDeploy2", latest.webUrl)
            assertEquals(DeployStatus.SUCCESS, docs.deployments.single { it.nativeId.endsWith("1") }.status)

            val blog = sites[1]
            assertNull(blog.repoUrl)
            assertTrue(blog.deployments.isEmpty())

            val deploymentRequests = requests.filter { it.url.encodedPath == "/v7/deployments" }
            assertEquals(
                setOf("prj_fakeDocs00000000000001", "prj_fakeBlog00000000000002"),
                deploymentRequests.map { it.url.parameters["projectId"] }.toSet(),
            )
            assertTrue(requests.none { "slug" in it.url.parameters.names() })
        }

    @Test
    fun teamSlugScopesEveryRequestAndLinksProjects() =
        runTest {
            val sites = vercel().fetchSnapshot(account(mapOf("team" to "acme"))).getOrThrow().sites

            assertTrue(requests.all { it.url.parameters["slug"] == "acme" })
            assertEquals("https://vercel.com/acme/acme-docs", sites[0].webUrl)
        }

    @Test
    fun onlyTalksToVercelOverHttps() =
        runTest {
            val vercel = vercel()

            vercel.validateCredentials(VercelFixtures.TOKEN)
            vercel.fetchSnapshot(account())

            assertTrue(requests.isNotEmpty())
            assertTrue(requests.all { it.url.protocol == URLProtocol.HTTPS && it.url.host == "api.vercel.com" })
        }
}
