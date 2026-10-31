package dev.dache.plugin.runtime

import dev.dache.core.account.tokenKey
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.PermissionCheck
import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ResourceKind
import dev.dache.core.model.ServicePage
import dev.dache.core.model.ServiceStatus
import dev.dache.core.model.TokenPermission
import dev.dache.plugin.api.ManifestJson
import dev.dache.plugin.api.PluginManifest
import dev.dache.plugin.api.ServiceSpec
import dev.dache.plugin.runtime.http.createPluginHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Plugin-described services, optional lists, token checks and pages. Fake host, fake data. */
class ServicesTest {
    private val account = ConnectedAccount(id = "acc-1", providerId = ProviderId.CLOUDFLARE, displayName = "dev")
    private val vault = MapTokenVault().apply { tokens[account.tokenKey()] = "fake-token-123" }

    private val manifest: PluginManifest =
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
              "capabilities": ["DOMAINS", "DNS", "SERVICES"],
              "validate": {
                "request": { "url": "https://api.example.com/user" },
                "displayName": "email",
                "permission": "Account Read"
              },
              "resources": [
                {
                  "kind": "domain",
                  "permission": "Zone Read",
                  "request": { "url": "https://api.example.com/zones" },
                  "fields": {
                    "nativeId": "id",
                    "name": "name",
                    "certificates": {
                      "request": { "url": "https://api.example.com/zones/{id}/certs" },
                      "permission": "Certificates Read",
                      "optional": true,
                      "from": "certs",
                      "fields": { "nativeId": "id", "status": "state" },
                      "statusMap": { "ok": "ACTIVE" }
                    }
                  }
                },
                {
                  "kind": "dnsRecord",
                  "permission": "DNS Read",
                  "forEach": { "request": { "url": "https://api.example.com/zones" } },
                  "request": { "url": "https://api.example.com/zones/{id}/records" },
                  "fields": { "nativeId": "id", "zoneName": "^.name", "type": "type", "name": "name", "content": "content" }
                },
                {
                  "kind": "service",
                  "service": { "id": "bucket", "label": "Buckets" },
                  "permission": "Storage Read",
                  "optional": true,
                  "request": { "url": "https://api.example.com/buckets" },
                  "items": "buckets",
                  "fields": { "nativeId": "name", "name": "name", "detail": "region", "createdAt": "created | date" }
                },
                {
                  "kind": "service",
                  "service": { "id": "tunnel", "label": "Tunnels" },
                  "permission": "Tunnel Read",
                  "optional": true,
                  "request": { "url": "https://api.example.com/tunnels" },
                  "fields": { "nativeId": "id", "name": "name", "status": "state" },
                  "statusMap": { "up": "HEALTHY", "down": "DOWN" }
                }
              ],
              "pages": [
                { "id": "dns", "title": "DNS", "resources": ["domain", "dnsRecord"] },
                { "id": "storage", "title": "Storage", "resources": ["bucket"] },
                { "id": "tunnels", "title": "Tunnels", "resources": ["tunnel"] }
              ]
            }
            """.trimIndent(),
        )

    private val bodies =
        mapOf(
            "/user" to """{"email": "dev@example.dev"}""",
            "/zones" to """[{"id": "z1", "name": "example.dev"}, {"id": "z2", "name": "example.org"}]""",
            "/zones/z1/certs" to """{"certs": [{"id": "c1", "state": "ok"}]}""",
            "/zones/z2/certs" to """{"certs": []}""",
            "/zones/z1/records" to """[{"id": "r1", "type": "A", "name": "example.dev", "content": "203.0.113.1"}]""",
            "/zones/z2/records" to "[]",
            "/buckets" to """{"buckets": [{"name": "assets", "region": "WEUR", "created": "2026-01-02T00:00:00Z"}]}""",
            "/tunnels" to """[{"id": "t1", "name": "office", "state": "down"}, {"id": "t2", "name": "lab"}]""",
        )

    /** Paths in [denied] answer 403, as when the token lacks that permission. Records every path requested. */
    private fun provider(
        denied: Set<String> = emptySet(),
        requested: MutableList<String> = mutableListOf(),
        failing: Map<String, HttpStatusCode> = emptyMap(),
        body: (String) -> String? = bodies::get,
    ): ManifestProvider {
        val engine =
            MockEngine { request ->
                val path = request.url.encodedPath
                requested += path
                val json = headersOf(HttpHeaders.ContentType, "application/json")
                if (path in denied || path in failing) {
                    respond("{}", failing[path] ?: HttpStatusCode.Forbidden, json)
                } else {
                    respond(body(path) ?: "[]", HttpStatusCode.OK, json)
                }
            }
        return ManifestProvider(manifest, ProviderId.CLOUDFLARE, vault, createPluginHttpClient(engine))
    }

    @Test
    fun manifestIsValid() {
        assertEquals(emptyList(), ManifestValidator.validate(manifest))
    }

    @Test
    fun mapsServicesWithPrefixedIds() =
        runTest {
            val snapshot = provider().fetchSnapshot(account).getOrThrow()

            val bucket = snapshot.services.first { it.service == "bucket" }
            assertEquals("bucket/assets", bucket.nativeId)
            assertEquals("Buckets", bucket.serviceLabel)
            assertEquals("WEUR", bucket.detail)
            assertEquals(Instant.parse("2026-01-02T00:00:00Z"), bucket.createdAt)
            assertEquals(null, bucket.status)
            assertEquals(ResourceKind.SERVICE, bucket.kind)
            assertEquals(
                listOf(ServiceStatus.DOWN, null),
                snapshot.services.filter { it.service == "tunnel" }.map { it.status },
            )
            assertEquals(emptySet(), snapshot.missingPermissions)
        }

    @Test
    fun anOptionalListTheTokenCantReadIsLeftOut() =
        runTest {
            val snapshot = provider(denied = setOf("/buckets")).fetchSnapshot(account).getOrThrow()

            assertEquals(listOf("tunnel", "tunnel"), snapshot.services.map { it.service })
            assertEquals(setOf("Storage Read"), snapshot.missingPermissions)
            assertEquals(2, snapshot.domains.size)
        }

    @Test
    fun anOptionalNestedListTheTokenCantReadLeavesItsFieldEmpty() =
        runTest {
            val snapshot = provider(denied = setOf("/zones/z1/certs")).fetchSnapshot(account).getOrThrow()

            assertEquals(emptyList(), snapshot.domains.first { it.name == "example.dev" }.certificates)
            assertEquals(setOf("Certificates Read"), snapshot.missingPermissions)
        }

    @Test
    fun anOptionalListCanNeverFailTheRefresh() =
        runTest {
            listOf(
                HttpStatusCode.Unauthorized,
                HttpStatusCode.BadRequest,
                HttpStatusCode.InternalServerError,
                HttpStatusCode.TooManyRequests,
            ).forEach { status ->
                val snapshot =
                    provider(failing = mapOf("/buckets" to status, "/zones/z1/certs" to status))
                        .fetchSnapshot(account)
                        .getOrThrow()

                assertEquals(2, snapshot.domains.size, "$status")
                assertEquals(2, snapshot.services.count { it.service == "tunnel" }, "$status")
            }
        }

    @Test
    fun refusalsAreMissingPermissionsAndOtherFailuresAreUnavailable() =
        runTest {
            val snapshot =
                provider(
                    failing = mapOf("/buckets" to HttpStatusCode.Unauthorized, "/tunnels" to HttpStatusCode.BadGateway),
                ).fetchSnapshot(account).getOrThrow()

            assertEquals(setOf("Storage Read"), snapshot.missingPermissions)
            assertEquals(setOf("Tunnel Read"), snapshot.unavailable)
        }

    @Test
    fun aRequiredListTheTokenCantReadStillFailsTheRefresh() =
        runTest {
            val result = provider(denied = setOf("/zones")).fetchSnapshot(account)

            assertIs<ProviderError.MissingScope>(result.exceptionOrNull())
        }

    @Test
    fun checkingATokenListsEachPermission() =
        runTest {
            val requested = mutableListOf<String>()
            val check =
                provider(denied = setOf("/buckets", "/zones/z1/records"), requested = requested)
                    .checkToken("fake-token-123")
                    .getOrThrow()

            assertEquals("dev@example.dev", check.displayName)
            assertEquals(
                listOf(
                    PermissionCheck(TokenPermission("Account Read", required = true), granted = true),
                    PermissionCheck(TokenPermission("Zone Read", required = true), granted = true),
                    PermissionCheck(TokenPermission("Certificates Read", required = false), granted = true),
                    PermissionCheck(TokenPermission("DNS Read", required = true), granted = false),
                    PermissionCheck(TokenPermission("Storage Read", required = false), granted = false),
                    PermissionCheck(TokenPermission("Tunnel Read", required = false), granted = true),
                ),
                check.permissions,
            )
            assertTrue(check.missingRequired)
            // Fanned-out lists are checked with the first parent only.
            assertTrue("/zones/z2/records" !in requested, "$requested")
            assertTrue("/zones/z2/certs" !in requested, "$requested")
        }

    @Test
    fun aPermissionWithNothingToCheckItAgainstIsUnknown() =
        runTest {
            val check =
                provider { path -> if (path == "/zones") "[]" else bodies[path] }
                    .checkToken("fake-token-123")
                    .getOrThrow()

            val unknown = check.permissions.filter { it.granted == null }.map { it.permission.name }
            assertEquals(listOf("Certificates Read", "DNS Read"), unknown)
            assertTrue(!check.missingRequired)
        }

    @Test
    fun aRefusedTokenStillGetsAChecklist() =
        runTest {
            val check = provider(denied = setOf("/user")).checkToken("fake-token-123").getOrThrow()

            assertEquals(null, check.displayName)
            assertEquals(
                listOf(false, null, null, null, null, null),
                check.permissions.map { it.granted },
            )
            assertEquals(
                "Account Read",
                check.permissions
                    .first()
                    .permission.name,
            )
            assertTrue(!check.usable)
        }

    @Test
    fun theDeclaredPermissionsMatchTheCheck() =
        runTest {
            val provider = provider()

            assertEquals(
                provider
                    .checkToken("fake-token-123")
                    .getOrThrow()
                    .permissions
                    .map { it.permission },
                provider.tokenPermissions,
            )
        }

    @Test
    fun declaredPagesGroupKindsAndServices() {
        assertEquals(
            listOf(
                ServicePage(
                    id = "dns",
                    title = "DNS",
                    kinds = setOf(ResourceKind.DOMAIN, ResourceKind.DNS_RECORD),
                    permissions = setOf("Zone Read", "Certificates Read", "DNS Read"),
                ),
                ServicePage("storage", "Storage", services = setOf("bucket"), permissions = setOf("Storage Read")),
                ServicePage("tunnels", "Tunnels", services = setOf("tunnel"), permissions = setOf("Tunnel Read")),
            ),
            provider().pages,
        )
    }

    @Test
    fun withoutPagesEachKindAndServiceGetsOne() {
        val pages = manifestPages(manifest.copy(pages = emptyList()))

        assertEquals(listOf("domain", "dnsRecord", "bucket", "tunnel"), pages.map { it.id })
        assertEquals(listOf(null, null, "Buckets", "Tunnels"), pages.map { it.title })
    }

    @Test
    fun rejectsBadServicesAndPages() {
        fun problems(manifest: PluginManifest) = ManifestValidator.validate(manifest).map { it.toString() }

        val bucket = manifest.resources[2]
        val domain = manifest.resources[0]
        val cases =
            mapOf(
                "resources[2].service: a service resource needs a service" to
                    manifest.copy(
                        resources =
                            manifest.resources.map {
                                if (it ==
                                    bucket
                                ) {
                                    it.copy(service = null)
                                } else {
                                    it
                                }
                            },
                    ),
                "resources[0].service: only service resources have one" to
                    manifest.copy(
                        resources =
                            manifest.resources.map {
                                if (it == domain) it.copy(service = ServiceSpec("zone", "Zones")) else it
                            },
                    ),
                "resources[2].permission: an optional list must name its permission" to
                    manifest.copy(
                        resources =
                            manifest.resources.map {
                                if (it ==
                                    bucket
                                ) {
                                    it.copy(permission = null)
                                } else {
                                    it
                                }
                            },
                    ),
                "pages[0].resources: \"queue\" is not a kind or service id of any resource" to
                    manifest.copy(
                        pages =
                            listOf(manifest.pages[0].copy(resources = listOf("domain", "dnsRecord", "queue"))) +
                                manifest.pages.drop(1),
                    ),
                "pages: \"tunnel\" is on no page" to manifest.copy(pages = manifest.pages.dropLast(1)),
                "pages: page id \"dns\" is used twice" to manifest.copy(pages = manifest.pages + manifest.pages[0]),
                "resources: service id \"bucket\" is used twice" to
                    manifest.copy(resources = manifest.resources + bucket),
            )
        cases.forEach { (expected, broken) ->
            val actual = problems(broken)
            assertTrue(actual.any { expected in it }, "expected \"$expected\" in $actual")
        }
    }
}
