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
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class GitHubPluginTest {
    private val vault = MapTokenVault()
    private val requests = mutableListOf<HttpRequestData>()

    private val account =
        ConnectedAccount(id = "acc-1", providerId = ProviderId.GITHUB, displayName = "acme-dev").also {
            vault.tokens[it.tokenKey()] = TOKEN
        }

    /** Paths in [denied] answer 403, as when a fine-grained token lacks that permission. */
    private fun github(denied: Set<String> = emptySet()): DevOpsProvider {
        val engine =
            MockEngine { request ->
                requests += request
                val path = request.url.encodedPath
                val authorized = request.headers[HttpHeaders.Authorization] == "Bearer $TOKEN"
                val (status, body) =
                    when {
                        !authorized -> HttpStatusCode.Unauthorized to """{"message":"Bad credentials"}"""
                        path in denied -> HttpStatusCode.Forbidden to FORBIDDEN
                        path == "/user" -> HttpStatusCode.OK to """{"login": "acme-dev", "id": 1}"""
                        path == "/user/repos" -> HttpStatusCode.OK to REPOS
                        path == "/repos/acme/app/actions/runs" -> HttpStatusCode.OK to RUNS
                        path == "/repos/acme/app/deployments" -> HttpStatusCode.OK to DEPLOYMENTS
                        path.endsWith("/actions/runs") ->
                            HttpStatusCode.OK to
                                """{"total_count": 0, "workflow_runs": []}"""
                        path.endsWith("/deployments") -> HttpStatusCode.OK to "[]"
                        else -> HttpStatusCode.NotFound to """{"message":"Not Found"}"""
                    }
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        return checkNotNull(BundledPlugins.providers(vault, createPluginHttpClient(engine))[ProviderId.GITHUB])
    }

    @Test
    fun bundledManifestLoads() {
        val github = github()

        assertEquals(setOf(Capability.CI, Capability.SERVICES), github.capabilities)
        assertEquals(listOf("actions", "deployments", "repositories"), github.pages.map { it.id })
    }

    @Test
    fun validTokenNamesTheAccountByLogin() =
        runTest {
            assertEquals("acme-dev", github().validateCredentials(TOKEN).getOrThrow().displayName)
        }

    @Test
    fun invalidTokenIsUnauthorized() =
        runTest {
            assertIs<ProviderError.Unauthorized>(github().validateCredentials("nope").exceptionOrNull())
        }

    @Test
    fun everyRequestAsksForTheVersionedApi() =
        runTest {
            github().fetchSnapshot(account).getOrThrow()

            assertTrue(requests.isNotEmpty())
            requests.forEach {
                assertEquals("application/vnd.github+json", it.headers[HttpHeaders.Accept])
                assertEquals("2022-11-28", it.headers["X-GitHub-Api-Version"])
            }
        }

    @Test
    fun listsWorkflowRunsPerRepository() =
        runTest {
            val runs = github().fetchSnapshot(account).getOrThrow().ciRuns

            assertEquals(listOf("CI", "Release"), runs.map { it.workflowName })
            assertEquals(listOf(DeployStatus.FAILED, DeployStatus.BUILDING), runs.map { it.status })
            val failed = runs[0]
            assertEquals("acme/app", failed.repoFullName)
            assertEquals("https://github.com/acme/app", failed.repoUrl)
            assertEquals("main", failed.branch)
            assertEquals("https://github.com/acme/app/actions/runs/101", failed.webUrl)
            assertEquals(Instant.parse("2026-10-30T09:00:00Z"), failed.createdAt)
        }

    @Test
    fun listsRepositoriesAndDeployments() =
        runTest {
            val services = github().fetchSnapshot(account).getOrThrow().services

            assertEquals(
                listOf("acme/app", "acme/docs"),
                services.filter { it.service == "repository" }.map { it.name },
            )
            val deployment = services.single { it.service == "deployment" }
            assertEquals("acme/app · production", deployment.name)
            assertEquals("v1.4.0", deployment.detail)
            assertEquals("https://github.com/acme/app/deployments", deployment.webUrl)
        }

    @Test
    fun aTokenWithoutActionsStillShowsRepositories() =
        runTest {
            val denied = setOf("/repos/acme/app/actions/runs", "/repos/acme/docs/actions/runs")

            val snapshot = github(denied).fetchSnapshot(account).getOrThrow()

            assertEquals(emptyList(), snapshot.ciRuns)
            assertEquals(setOf("Actions"), snapshot.missingPermissions)
            assertEquals(2, snapshot.services.count { it.service == "repository" })
        }

    @Test
    fun checkingTheTokenListsItsPermissions() =
        runTest {
            val check = github(denied = setOf("/repos/acme/app/deployments")).checkToken(TOKEN).getOrThrow()

            assertEquals(
                mapOf("Metadata" to true, "Actions" to true, "Deployments" to false),
                check.permissions.associate { it.permission.name to it.granted },
            )
            assertEquals(listOf(true, false, false), check.permissions.map { it.permission.required })
        }

    private companion object {
        const val TOKEN = "github_pat_fake_token_for_tests_only"

        const val FORBIDDEN = """{"message":"Resource not accessible by personal access token"}"""

        const val REPOS = """[
          {"id": 1, "name": "app", "full_name": "acme/app", "owner": {"login": "acme"},
           "html_url": "https://github.com/acme/app", "language": "Kotlin", "pushed_at": "2026-10-31T08:00:00Z"},
          {"id": 2, "name": "docs", "full_name": "acme/docs", "owner": {"login": "acme"},
           "html_url": "https://github.com/acme/docs", "language": null, "pushed_at": "2026-10-01T08:00:00Z"}
        ]"""

        const val RUNS = """{"total_count": 2, "workflow_runs": [
          {"id": 101, "name": "CI", "head_branch": "main", "status": "completed", "conclusion": "failure",
           "html_url": "https://github.com/acme/app/actions/runs/101", "created_at": "2026-10-30T09:00:00Z"},
          {"id": 102, "name": "Release", "head_branch": "v1.4.0", "status": "in_progress", "conclusion": null,
           "html_url": "https://github.com/acme/app/actions/runs/102", "created_at": "2026-10-31T09:00:00Z"}
        ]}"""

        const val DEPLOYMENTS = """[
          {"id": 9001, "environment": "production", "ref": "v1.4.0",
           "created_at": "2026-10-31T09:30:00Z", "updated_at": "2026-10-31T09:35:00Z"}
        ]"""
    }
}
