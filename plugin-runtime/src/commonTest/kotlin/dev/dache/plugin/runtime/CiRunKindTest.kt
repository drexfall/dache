package dev.dache.plugin.runtime

import dev.dache.core.account.tokenKey
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.DeployStatus
import dev.dache.core.model.ProviderId
import dev.dache.plugin.api.ManifestJson
import dev.dache.plugin.runtime.http.createPluginHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** CI runs fanned out per repository, the shape of GitHub Actions. Fake host, fake data. */
class CiRunKindTest {
    private val account = ConnectedAccount(id = "acc-1", providerId = ProviderId.GITHUB, displayName = "dev")
    private val vault = MapTokenVault().apply { tokens[account.tokenKey()] = "fake-token-123" }

    private val manifest =
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
              "capabilities": ["CI"],
              "validate": { "request": { "url": "https://api.example.com/user" }, "displayName": "login" },
              "resources": [
                {
                  "kind": "ciRun",
                  "forEach": { "request": { "url": "https://api.example.com/repos" } },
                  "request": { "url": "https://api.example.com/repos/{owner.login}/{name}/runs" },
                  "items": "workflow_runs",
                  "fields": {
                    "nativeId": "id",
                    "webUrl": "html_url",
                    "repoFullName": "^.full_name",
                    "repoUrl": "^.html_url",
                    "workflowName": "name",
                    "branch": "head_branch",
                    "status": "conclusion | default:running",
                    "createdAt": "created_at | date"
                  },
                  "statusMap": { "success": "SUCCESS", "failure": "FAILED", "running": "BUILDING" }
                }
              ]
            }
            """.trimIndent(),
        )

    @Test
    fun manifestIsValid() {
        assertEquals(emptyList(), ManifestValidator.validate(manifest))
    }

    @Test
    fun mapsRunsWithTheirRepository() =
        runTest {
            val engine =
                MockEngine { request ->
                    val body =
                        when (request.url.encodedPath) {
                            "/repos" ->
                                """[{"name": "app", "full_name": "acme/app", "owner": {"login": "acme"},
                                     "html_url": "https://github.com/acme/app"}]"""
                            "/repos/acme/app/runs" ->
                                """{"workflow_runs": [
                                  {"id": 11, "html_url": "https://github.com/acme/app/actions/runs/11", "name": "CI",
                                   "head_branch": "main", "conclusion": "failure", "created_at": "2026-10-30T10:00:00Z"},
                                  {"id": 12, "html_url": "https://github.com/acme/app/actions/runs/12", "name": "CI",
                                   "head_branch": "main", "conclusion": null, "created_at": "2026-10-31T10:00:00Z"}
                                ]}"""
                            else -> "[]"
                        }
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val provider = ManifestProvider(manifest, ProviderId.GITHUB, vault, createPluginHttpClient(engine))

            val runs = provider.fetchSnapshot(account).getOrThrow().ciRuns

            assertEquals(listOf("11", "12"), runs.map { it.nativeId })
            assertEquals(listOf(DeployStatus.FAILED, DeployStatus.BUILDING), runs.map { it.status })
            assertEquals("acme/app", runs[0].repoFullName)
            assertEquals("https://github.com/acme/app", runs[0].repoUrl)
            assertEquals("CI", runs[0].workflowName)
            assertEquals(Instant.parse("2026-10-30T10:00:00Z"), runs[0].createdAt)
        }
}
