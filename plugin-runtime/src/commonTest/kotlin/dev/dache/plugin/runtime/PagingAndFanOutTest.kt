package dev.dache.plugin.runtime

import dev.dache.core.account.tokenKey
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.core.model.Site
import dev.dache.plugin.api.ManifestJson
import dev.dache.plugin.api.PluginManifest
import dev.dache.plugin.runtime.http.createPluginHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PagingAndFanOutTest {
    private val account = ConnectedAccount(id = "acc-1", providerId = ProviderId.VERCEL, displayName = "dev")
    private val vault = MapTokenVault().apply { tokens[account.tokenKey()] = "fake-token-123" }
    private val requests = mutableListOf<HttpRequestData>()

    private val siteFields = """"nativeId": "id", "name": "name", "webUrl": "url", "primaryUrl": "url""""

    private fun manifest(resource: String): PluginManifest =
        ManifestJson
            .decode(
                """
                {
                  "manifestVersion": 1,
                  "id": "example",
                  "name": "Example",
                  "version": "1.0.0",
                  "hosts": ["api.example.com"],
                  "auth": { "type": "bearer" },
                  "capabilities": ["SITES", "DEPLOYMENTS"],
                  "validate": { "request": { "url": "https://api.example.com/user" }, "displayName": "email" },
                  "resources": [$resource]
                }
                """.trimIndent(),
            ).also { assertEquals(emptyList(), ManifestValidator.validate(it)) }

    private suspend fun fetch(
        resource: String,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Result<List<Site>> =
        ManifestProvider(
            manifest = manifest(resource),
            id = ProviderId.VERCEL,
            vault = vault,
            client =
                createPluginHttpClient(
                    MockEngine { request ->
                        requests += request
                        handler(request)
                    },
                ),
        ).fetchSnapshot(account).map { it.sites }

    private fun MockRequestHandleScope.json(
        body: String,
        vararg headers: Pair<String, String>,
    ) = respond(
        body,
        HttpStatusCode.OK,
        headersOf(
            *(headers.toList() + (HttpHeaders.ContentType to "application/json"))
                .map {
                    it.first to
                        listOf(it.second)
                }.toTypedArray(),
        ),
    )

    private fun site(id: String) = """{"id": "$id", "name": "site-$id", "url": "https://$id.example.app"}"""

    private fun sites(vararg ids: String) = ids.joinToString(prefix = "[", postfix = "]", transform = ::site)

    private fun deploy(id: String) =
        """{"uid": "$id", "url": "https://$id.example.app", "state": "READY", "created": "2026-10-27T09:30:00Z"}"""

    // Cursor paging

    private val cursorResource =
        """
        {
          "kind": "site",
          "request": {
            "url": "https://api.example.com/projects",
            "paging": { "type": "cursor", "param": "until", "next": "pagination.next", "sizeParam": "limit", "size": 2, "maxPages": 3 }
          },
          "items": "projects",
          "fields": { $siteFields }
        }
        """.trimIndent()

    private fun cursorPage(
        next: String,
        vararg ids: String,
    ) = """{"projects": ${sites(*ids)}, "pagination": {"next": $next}}"""

    @Test
    fun cursorPagingFollowsNextUntilItIsMissing() =
        runTest {
            val sites =
                fetch(cursorResource) { request ->
                    when (request.url.parameters["until"]) {
                        null -> json(cursorPage("1730000000000", "a", "b"))
                        "1730000000000" -> json(cursorPage("null", "c"))
                        else -> respondError(HttpStatusCode.BadRequest)
                    }
                }.getOrThrow()

            assertEquals(listOf("a", "b", "c"), sites.map { it.nativeId })
            assertEquals(listOf(null, "1730000000000"), requests.map { it.url.parameters["until"] })
            assertTrue(requests.all { it.url.parameters["limit"] == "2" })
        }

    @Test
    fun cursorPagingStopsOnARepeatedCursor() =
        runTest {
            fetch(cursorResource) { json(cursorPage("\"same\"", "a", "b")) }.getOrThrow()

            assertEquals(listOf(null, "same"), requests.map { it.url.parameters["until"] })
        }

    @Test
    fun cursorPagingStopsAtMaxPages() =
        runTest {
            fetch(cursorResource) { json(cursorPage("${requests.size}", "a", "b")) }.getOrThrow()

            assertEquals(3, requests.size)
        }

    // Link-header paging

    private val linkResource =
        """
        {
          "kind": "site",
          "request": {
            "url": "https://api.example.com/repos",
            "paging": { "type": "link", "sizeParam": "per_page", "size": 2 }
          },
          "fields": { $siteFields }
        }
        """.trimIndent()

    @Test
    fun linkPagingFollowsTheNextLink() =
        runTest {
            val sites =
                fetch(linkResource) { request ->
                    when (request.url.parameters["page"]) {
                        null ->
                            json(
                                sites("a", "b"),
                                HttpHeaders.Link to
                                    "<https://api.example.com/repos?per_page=2&page=2>; rel=\"next\", " +
                                    "<https://api.example.com/repos?per_page=2&page=2>; rel=\"last\"",
                            )
                        "2" -> json(sites("c"), HttpHeaders.Link to "</repos?per_page=2&page=1>; rel=\"prev\"")
                        else -> respondError(HttpStatusCode.BadRequest)
                    }
                }.getOrThrow()

            assertEquals(listOf("a", "b", "c"), sites.map { it.nativeId })
            assertEquals(listOf("2", "2"), requests.map { it.url.parameters["per_page"] })
            assertTrue(requests.all { it.headers[HttpHeaders.Authorization] == "Bearer fake-token-123" })
        }

    @Test
    fun linkPagingStopsWhenTheNextLinkRepeats() =
        runTest {
            fetch(linkResource) { request ->
                json(sites("a"), HttpHeaders.Link to "<${request.url}>; rel=\"next\"")
            }.getOrThrow()

            assertEquals(1, requests.size)
        }

    @Test
    fun nextLinkToAnUndeclaredHostIsNeverFollowed() =
        runTest {
            val result =
                fetch(linkResource) {
                    json(sites("a"), HttpHeaders.Link to "<https://evil.example.net/repos?page=2>; rel=\"next\"")
                }

            assertIs<ProviderError.Unknown>(result.exceptionOrNull())
            assertEquals(listOf("api.example.com"), requests.map { it.url.host })
        }

    // Fan-out

    private val perAccountResource =
        """
        {
          "kind": "site",
          "forEach": { "request": { "url": "https://api.example.com/accounts" }, "items": "result" },
          "request": { "url": "https://api.example.com/accounts/{id}/projects", "query": { "owner": "{name}" } },
          "items": "result",
          "fields": { $siteFields }
        }
        """.trimIndent()

    private val slashedAccount = """{"id": "a/2", "name": "T&o"}"""

    @Test
    fun forEachSendsOneRequestPerParent() =
        runTest {
            val sites =
                fetch(perAccountResource) { request ->
                    when (request.url.encodedPath) {
                        "/accounts" ->
                            json("""{"result": [{"id": "a1", "name": "One"}, $slashedAccount, {"name": "no id"}]}""")
                        "/accounts/a1/projects" -> json("""{"result": ${sites("x", "y")}}""")
                        "/accounts/a%2F2/projects" -> json("""{"result": ${sites("z")}}""")
                        else -> respondError(HttpStatusCode.BadRequest)
                    }
                }.getOrThrow()

            assertEquals(listOf("x", "y", "z"), sites.map { it.nativeId })
            assertEquals(
                listOf("/accounts", "/accounts/a1/projects", "/accounts/a%2F2/projects"),
                requests.map { it.url.encodedPath },
            )
            assertEquals("T&o", requests.last().url.parameters["owner"])
        }

    @Test
    fun forEachRefusesValuesThatWouldMoveThePath() =
        runTest {
            val sites =
                fetch(perAccountResource) { request ->
                    when (request.url.encodedPath) {
                        "/accounts" -> json("""{"result": [{"id": "..", "name": "up"}, {"id": "", "name": "empty"}]}""")
                        else -> respondError(HttpStatusCode.BadRequest)
                    }
                }.getOrThrow()

            assertTrue(sites.isEmpty())
            assertEquals(listOf("/accounts"), requests.map { it.url.encodedPath })
        }

    @Test
    fun aParentWhoseListIsGoneHasNoItems() =
        runTest {
            val sites =
                fetch(perAccountResource) { request ->
                    when (request.url.encodedPath) {
                        "/accounts" -> json("""{"result": [{"id": "gone", "name": "g"}, {"id": "a1", "name": "o"}]}""")
                        "/accounts/a1/projects" -> json("""{"result": ${sites("x")}}""")
                        else -> respondError(HttpStatusCode.NotFound)
                    }
                }.getOrThrow()

            assertEquals(listOf("x"), sites.map { it.nativeId })
        }

    @Test
    fun aMissingParentListStillFails() =
        runTest {
            val result = fetch(perAccountResource) { respondError(HttpStatusCode.NotFound) }

            assertIs<ProviderError.NotFound>(result.exceptionOrNull())
        }

    @Test
    fun fannedOutItemsReadTheirParentAsCaret() =
        runTest {
            val resource =
                """
                {
                  "kind": "site",
                  "forEach": { "request": { "url": "https://api.example.com/accounts" }, "items": "result" },
                  "request": { "url": "https://api.example.com/accounts/{id}/projects" },
                  "items": "result",
                  "fields": {
                    "nativeId": "id",
                    "name": "^.name",
                    "webUrl": "https://dash.example.com/{^.id}/{id}",
                    "primaryUrl": "url"
                  }
                }
                """.trimIndent()

            val sites =
                fetch(resource) { request ->
                    when (request.url.encodedPath) {
                        "/accounts" -> json("""{"result": [{"id": "a1", "name": "One"}]}""")
                        else -> json("""{"result": ${sites("x")}}""")
                    }
                }.getOrThrow()

            assertEquals("One", sites.single().name)
            assertEquals("https://dash.example.com/a1/x", sites.single().webUrl)
        }

    private val deploymentsPerSiteResource =
        """
        {
          "kind": "site",
          "request": { "url": "https://api.example.com/projects" },
          "fields": {
            $siteFields,
            "deployments": {
              "request": { "url": "https://api.example.com/deployments", "query": { "projectId": "{id}", "limit": "5" } },
              "from": "deployments",
              "fields": { "nativeId": "uid", "webUrl": "url", "status": "state", "createdAt": "created | date" },
              "statusMap": { "READY": "SUCCESS" }
            }
          }
        }
        """.trimIndent()

    @Test
    fun nestedRequestFetchesRecordsPerItem() =
        runTest {
            val sites =
                fetch(deploymentsPerSiteResource) { request ->
                    when (request.url.encodedPath to request.url.parameters["projectId"]) {
                        "/projects" to null -> json(sites("a", "b", "gone"))
                        "/deployments" to "a" -> json("""{"deployments": [${deploy("d1")}, ${deploy("d2")}]}""")
                        "/deployments" to "b" -> json("""{"deployments": []}""")
                        else -> respondError(HttpStatusCode.NotFound)
                    }
                }.getOrThrow()

            assertEquals(listOf("a", "b", "gone"), sites.map { it.nativeId })
            assertEquals(listOf("d1", "d2"), sites[0].deployments.map { it.nativeId })
            assertTrue(sites[1].deployments.isEmpty())
            assertTrue(sites[2].deployments.isEmpty())
            assertTrue(requests.drop(1).all { it.url.parameters["limit"] == "5" })
        }

    @Test
    fun nestedRequestFailureOtherThanNotFoundFailsTheRefresh() =
        runTest {
            val result =
                fetch(deploymentsPerSiteResource) { request ->
                    if (request.url.encodedPath == "/projects") {
                        json(sites("a"))
                    } else {
                        respondError(HttpStatusCode.Unauthorized)
                    }
                }

            assertIs<ProviderError.Unauthorized>(result.exceptionOrNull())
        }

    @Test
    fun fanOutStopsAtTheLimitButKeepsEveryItem() =
        runTest {
            val ids = (1..MAX_FAN_OUT + 5).map { "s$it" }.toTypedArray()

            val sites =
                fetch(deploymentsPerSiteResource) { request ->
                    if (request.url.encodedPath == "/projects") {
                        json(sites(*ids))
                    } else {
                        json("""{"deployments": [${deploy("d-${request.url.parameters["projectId"]}")}]}""")
                    }
                }.getOrThrow()

            assertEquals(ids.size, sites.size)
            assertEquals(MAX_FAN_OUT, requests.count { it.url.encodedPath == "/deployments" })
            assertEquals(
                "d-s1",
                sites
                    .first()
                    .deployments
                    .single()
                    .nativeId,
            )
            assertTrue(sites.last().deployments.isEmpty())
        }
}
