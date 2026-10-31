package dev.dache.plugin.runtime

import dev.dache.core.account.tokenKey
import dev.dache.core.model.ActionRequest
import dev.dache.core.model.ActionTarget
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ResourceKind
import dev.dache.core.model.ResourceRef
import dev.dache.plugin.api.ManifestJson
import dev.dache.plugin.api.PluginManifest
import dev.dache.plugin.runtime.http.createPluginHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ActionsTest {
    private val account = ConnectedAccount(id = "acc-1", providerId = ProviderId.NETLIFY, displayName = "dev")
    private val vault = MapTokenVault().apply { tokens[account.tokenKey()] = "fake-token-123" }
    private val writes = mutableListOf<HttpRequestData>()
    private val site = ActionTarget(ResourceRef(ProviderId.NETLIFY, "acc-1", "s1", ResourceKind.SITE))

    private val actions =
        """
        [
          { "id": "redeploy", "label": "Redeploy", "target": "site",
            "request": { "url": "https://api.example.com/sites/{id}/builds", "method": "POST" } },
          { "id": "restore", "label": "Restore", "target": "deployment", "destructive": true, "permission": "Deploy",
            "request": { "url": "https://api.example.com/sites/{^.id}/deploys/{id}/restore", "method": "POST" } },
          { "id": "rename", "label": "Rename", "target": "site",
            "inputs": [
              { "key": "name", "label": "Name", "default": "name" },
              { "key": "ttl", "label": "TTL", "type": "number", "default": "deploy_time | default:60" }
            ],
            "request": { "url": "https://api.example.com/sites/{id}", "method": "PATCH" },
            "body": { "name": "{input.name}", "ttl": "{input.ttl}", "note": "renamed from {name}" } }
        ]
        """.trimIndent()

    private fun manifest(extra: String = actions): PluginManifest {
        val base = Json.parseToJsonElement(TestManifests.sitesJson).jsonObject
        val json = JsonObject(base + ("actions" to Json.parseToJsonElement(extra)))
        return ManifestJson.decode(json.toString())
    }

    private fun provider(manifest: PluginManifest = manifest()) =
        ManifestProvider(
            manifest = manifest,
            vault = vault,
            client =
                createPluginHttpClient(
                    MockEngine { request ->
                        val body =
                            when {
                                request.method != HttpMethod.Get -> {
                                    writes += request
                                    "{}"
                                }
                                request.url.parameters["page"] == "1" ->
                                    """[{"id": "s1", "name": "docs", "admin_url": "https://x.example.com",
                                    "ssl_url": "https://docs.example.app", "published_deploy":
                                    {"id": "d1", "state": "ready", "created_at": "2026-10-27T09:30:00Z"}}]"""
                                else -> "[]"
                            }
                        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                ),
        )

    @Test
    fun aRedeploySendsThePostTheManifestDeclares() =
        runTest {
            val provider = provider()
            provider.fetchSnapshot(account).getOrThrow()

            provider.runAction(account, ActionRequest("redeploy", site)).getOrThrow()

            val write = writes.single()
            assertEquals(HttpMethod.Post, write.method)
            assertEquals("https://api.example.com/sites/s1/builds", write.url.toString())
            assertEquals("Bearer fake-token-123", write.headers[HttpHeaders.Authorization])
        }

    @Test
    fun aDeploymentActionReadsItsSiteAsTheParent() =
        runTest {
            val provider = provider()
            provider.fetchSnapshot(account).getOrThrow()

            provider.runAction(account, ActionRequest("restore", site.copy(deploymentId = "d1"))).getOrThrow()

            assertEquals("https://api.example.com/sites/s1/deploys/d1/restore", writes.single().url.toString())
            assertTrue(provider.actions.single { it.id == "restore" }.let { it.destructive && it.onDeployments })
        }

    @Test
    fun thePreviewShowsEachChangeAndTheTypedBody() =
        runTest {
            val provider = provider()
            provider.fetchSnapshot(account).getOrThrow()
            val request = ActionRequest("rename", site, mapOf("name" to "handbook", "ttl" to "300"))

            assertEquals(mapOf("name" to "docs", "ttl" to "60"), provider.actionDefaults(account, request).getOrThrow())
            val preview = provider.previewAction(account, request).getOrThrow()

            assertEquals("PATCH", preview.method)
            assertEquals(listOf("docs" to "handbook", "60" to "300"), preview.changes.map { it.before to it.after })
            provider.runAction(account, request).getOrThrow()
            val sent = Json.parseToJsonElement((writes.single().body as TextContent).text).jsonObject
            assertEquals("""{"name":"handbook","ttl":300,"note":"renamed from docs"}""", sent.toString())
            assertTrue(writes.isNotEmpty())
        }

    @Test
    fun nothingRunsBeforeTheTargetWasListed() =
        runTest {
            val error = provider().runAction(account, ActionRequest("redeploy", site)).exceptionOrNull()

            assertIs<ActionInputException>(error)
            assertTrue(writes.isEmpty())
        }

    @Test
    fun aMissingRequiredInputIsRefused() =
        runTest {
            val provider = provider()
            provider.fetchSnapshot(account).getOrThrow()

            val error =
                provider
                    .runAction(
                        account,
                        ActionRequest("rename", site, mapOf("ttl" to "5")),
                    ).exceptionOrNull()

            assertEquals("Name is required.", error?.message)
        }

    @Test
    fun theValidatorChecksMethodsTargetsInputsAndPermissions() {
        val broken =
            """
            [
              { "id": "a", "label": "A", "target": "site", "request": { "url": "https://api.example.com/s/{id}" } },
              { "id": "b", "label": "B", "target": "monitor",
                "request": { "url": "https://api.example.com/m/{id}", "method": "POST" } },
              { "id": "c", "label": "C", "target": "site", "destructive": true,
                "request": { "url": "https://api.example.com/s/{input.nope}", "method": "DELETE" } }
            ]
            """.trimIndent()

        val problems = ManifestValidator.validate(manifest(broken)).map { it.toString() }

        assertTrue(problems.any { it.startsWith("actions[0].request.method") }, problems.toString())
        assertTrue(problems.any { it.startsWith("actions[1].target") }, problems.toString())
        assertTrue(problems.any { it.startsWith("actions[2].permission") }, problems.toString())
        assertTrue(
            problems.any { it.startsWith("actions[2].request.url") && "names no input" in it },
            problems.toString(),
        )
        assertTrue(ManifestValidator.validate(manifest()).isEmpty())
    }
}
