package dev.dache.plugin.registry

import dev.dache.core.account.tokenKey
import dev.dache.core.model.Billing
import dev.dache.core.model.Capability
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.DeployStatus
import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ServerStatus
import dev.dache.core.provider.DevOpsProvider
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
import kotlin.test.assertNull

class DigitalOceanPluginTest {
    private val vault = MapTokenVault()

    private val account =
        ConnectedAccount(id = "acc-1", providerId = ProviderId.DIGITALOCEAN, displayName = "Acme").also {
            vault.tokens[it.tokenKey()] = TOKEN
        }

    /** Paths in [denied] answer 403, as when a custom-scoped token lacks that scope. */
    private fun digitalOcean(denied: Set<String> = emptySet()): DevOpsProvider {
        val engine =
            MockEngine { request ->
                val path = request.url.encodedPath
                val secondPage = (request.url.parameters["page"] ?: "1") != "1"
                val (status, body) =
                    when {
                        request.headers[HttpHeaders.Authorization] != "Bearer $TOKEN" ->
                            HttpStatusCode.Unauthorized to
                                """{"id":"unauthorized","message":"Unable to authenticate you"}"""
                        path in denied ->
                            HttpStatusCode.Forbidden to
                                """{"id":"forbidden","message":"You are not authorized"}"""
                        secondPage -> HttpStatusCode.OK to EMPTY_PAGES.getValue(path)
                        else -> RESPONSES[path]?.let { HttpStatusCode.OK to it } ?: (HttpStatusCode.NotFound to "{}")
                    }
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        return checkNotNull(BundledPlugins.providers(vault, createPluginHttpClient(engine))[ProviderId.DIGITALOCEAN])
    }

    @Test
    fun bundledManifestLoads() {
        val provider = digitalOcean()

        assertEquals(
            setOf(
                Capability.SERVERS,
                Capability.SITES,
                Capability.DEPLOYMENTS,
                Capability.DOMAINS,
                Capability.DNS,
                Capability.BILLING,
            ),
            provider.capabilities,
        )
        assertEquals(listOf("droplets", "apps", "dns"), provider.pages.map { it.id })
    }

    @Test
    fun validTokenNamesTheTeam() =
        runTest {
            assertEquals("Acme", digitalOcean().validateCredentials(TOKEN).getOrThrow().displayName)
        }

    @Test
    fun invalidTokenIsUnauthorized() =
        runTest {
            assertIs<ProviderError.Unauthorized>(digitalOcean().validateCredentials("nope").exceptionOrNull())
        }

    @Test
    fun listsDropletsWithTheirPublicAddress() =
        runTest {
            val droplet =
                digitalOcean()
                    .fetchSnapshot(account)
                    .getOrThrow()
                    .servers
                    .single()

            assertEquals("api-1", droplet.name)
            assertEquals("fra1", droplet.region)
            assertEquals(ServerStatus.ACTIVE, droplet.status)
            assertEquals("203.0.113.7", droplet.publicIpv4)
            assertEquals("https://cloud.digitalocean.com/droplets/301", droplet.webUrl)
        }

    @Test
    fun listsAppsWithTheirDeployments() =
        runTest {
            val app =
                digitalOcean()
                    .fetchSnapshot(account)
                    .getOrThrow()
                    .sites
                    .single()

            assertEquals("acme-web", app.name)
            assertEquals("https://acme-web-x1y2.ondigitalocean.app", app.primaryUrl)
            assertEquals(listOf("www.acme.dev"), app.customDomains)
            assertEquals("https://github.com/acme/web", app.repoUrl)
            assertEquals(listOf(DeployStatus.BUILDING, DeployStatus.FAILED), app.deployments.map { it.status })
        }

    @Test
    fun dnsRecordsCarryFullHostnames() =
        runTest {
            val snapshot = digitalOcean().fetchSnapshot(account).getOrThrow()

            assertEquals(listOf("acme.dev"), snapshot.domains.map { it.name })
            assertEquals(listOf("acme.dev", "www.acme.dev"), snapshot.dnsRecords.map { it.name })
            assertEquals("203.0.113.7", snapshot.dnsRecords[0].content)
        }

    @Test
    fun readsTheBalance() =
        runTest {
            assertEquals(
                Billing("USD", 23.45, -10.0, emptyList()),
                digitalOcean().fetchSnapshot(account).getOrThrow().billing,
            )
        }

    @Test
    fun aTokenWithoutBillingStillRefreshes() =
        runTest {
            val snapshot = digitalOcean(denied = setOf("/v2/customers/my/balance")).fetchSnapshot(account).getOrThrow()

            assertNull(snapshot.billing)
            assertEquals(setOf("billing:read"), snapshot.missingPermissions)
            assertEquals(1, snapshot.servers.size)
        }

    @Test
    fun checkingTheTokenListsItsScopes() =
        runTest {
            val check = digitalOcean(denied = setOf("/v2/apps")).checkToken(TOKEN).getOrThrow()

            assertEquals(
                mapOf(
                    "account:read" to true,
                    "droplet:read" to true,
                    "app:read" to false,
                    "domain:read" to true,
                    "billing:read" to true,
                ),
                check.permissions.associate { it.permission.name to it.granted },
            )
        }

    private companion object {
        const val TOKEN = "dop_v1_fake_token_for_tests_only"

        val RESPONSES =
            mapOf(
                "/v2/account" to """{"account": {"email": "dev@acme.dev", "team": {"name": "Acme"}}}""",
                "/v2/droplets" to
                    """{"droplets": [{"id": 301, "name": "api-1", "status": "active", "region": {"slug": "fra1"},
                        "networks": {"v4": [{"ip_address": "10.110.0.2", "type": "private"},
                                            {"ip_address": "203.0.113.7", "type": "public"}]}}]}""",
                "/v2/apps" to
                    """{"apps": [{"id": "app-1", "live_url": "https://acme-web-x1y2.ondigitalocean.app",
                        "spec": {"name": "acme-web", "domains": [{"domain": "www.acme.dev"}],
                                 "services": [{"github": {"repo": "acme/web", "branch": "main"}}]}}]}""",
                "/v2/apps/app-1/deployments" to
                    """{"deployments": [
                        {"id": "d2", "phase": "BUILDING", "cause": "commit 1a2b pushed", "created_at": "2026-10-31T10:00:00Z"},
                        {"id": "d1", "phase": "ERROR", "cause": "manual", "created_at": "2026-10-30T10:00:00Z"}]}""",
                "/v2/domains" to """{"domains": [{"name": "acme.dev", "ttl": 1800}]}""",
                "/v2/domains/acme.dev/records" to
                    """{"domain_records": [
                        {"id": 11, "type": "A", "name": "@", "data": "203.0.113.7"},
                        {"id": 12, "type": "CNAME", "name": "www", "data": "acme-web-x1y2.ondigitalocean.app."}
                    ]}""",
                "/v2/customers/my/balance" to
                    """{"month_to_date_balance": "13.45", "account_balance": "-10.00",
                        "month_to_date_usage": "23.45"}""",
            )

        val EMPTY_PAGES =
            mapOf(
                "/v2/droplets" to """{"droplets": []}""",
                "/v2/apps" to """{"apps": []}""",
                "/v2/domains" to """{"domains": []}""",
                "/v2/domains/acme.dev/records" to """{"domain_records": []}""",
            )
    }
}
