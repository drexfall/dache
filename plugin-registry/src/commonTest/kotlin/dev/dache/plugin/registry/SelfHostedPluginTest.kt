package dev.dache.plugin.registry

import dev.dache.core.account.tokenKey
import dev.dache.core.model.ActionRequest
import dev.dache.core.model.ActionTarget
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ServerStatus
import dev.dache.core.provider.DevOpsProvider
import dev.dache.plugin.runtime.SelfHostedProvider
import dev.dache.plugin.runtime.http.createPluginHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelfHostedPluginTest {
    private val vault = MapTokenVault()
    private val requests = mutableListOf<HttpRequestData>()
    private val token = "root@pam!dache=1234-abcd"

    private fun provider(id: String): DevOpsProvider {
        val engine =
            MockEngine { request ->
                requests += request
                val body =
                    when (request.url.encodedPath) {
                        "/api2/json/version" -> """{"data": {"version": "8.2.4", "release": "8.2"}}"""
                        "/api2/json/cluster/resources" ->
                            """{"data": [
                              {"id": "qemu/100", "vmid": 100, "name": "web", "node": "pve1", "type": "qemu", "status": "running"},
                              {"id": "lxc/101", "vmid": 101, "name": "db", "node": "pve2", "type": "lxc", "status": "stopped"}
                            ]}"""
                        "/api/v1/team" -> """{"id": 0, "name": "Root Team"}"""
                        "/api/v1/applications" ->
                            """[{"uuid": "a1", "name": "blog", "fqdn": "https://blog.lan",
                            "status": "running:healthy"}]"""
                        "/api/v1/servers" ->
                            """[{"uuid": "s1", "name": "localhost", "ip": "10.0.0.2",
                            "settings": {"is_reachable": true}}]"""
                        else -> "{}"
                    }
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        return checkNotNull(BundledPlugins.providers(vault, createPluginHttpClient(engine))[ProviderId(id)])
    }

    private fun account(
        id: String,
        server: String,
    ) = ConnectedAccount("acc-$id", ProviderId(id), "me", mapOf("server" to server)).also {
        vault.tokens[it.tokenKey()] = token
    }

    @Test
    fun proxmoxIsReachedOnItsOwnPortWithItsTokenHeader() =
        runTest {
            val proxmox = provider("proxmox")
            val account = account("proxmox", "https://PVE.lan:8006/")

            assertEquals("8.2.4", proxmox.validateCredentials(token, account.settings).getOrThrow().displayName)
            val servers = proxmox.fetchSnapshot(account).getOrThrow().servers

            assertTrue(
                requests.all { it.url.host == "pve.lan" && it.url.port == 8006 },
                requests.map { it.url }.toString(),
            )
            assertEquals("PVEAPIToken=$token", requests.first().headers[HttpHeaders.Authorization])
            assertEquals(
                listOf("web" to ServerStatus.ACTIVE, "db" to ServerStatus.OFF),
                servers.map {
                    it.name to
                        it.status
                },
            )
            assertEquals(listOf("pve1", "pve2"), servers.map { it.region })

            proxmox.runAction(account, ActionRequest("reboot", ActionTarget(servers.first().ref))).getOrThrow()
            val write = requests.single { it.method == HttpMethod.Post }
            assertEquals("https://pve.lan:8006/api2/json/nodes/pve1/qemu/100/status/reboot", write.url.toString())
        }

    @Test
    fun coolifyListsApplicationsAndDeploysThem() =
        runTest {
            val coolify = provider("coolify")
            val account = account("coolify", "coolify.example.com")
            val snapshot = coolify.fetchSnapshot(account).getOrThrow()
            val app = snapshot.services.single()

            assertEquals("blog", app.name)
            assertEquals("localhost", snapshot.servers.single().name)
            coolify.runAction(account, ActionRequest("deploy", ActionTarget(app.ref))).getOrThrow()

            assertEquals(
                "https://coolify.example.com/api/v1/deploy?uuid=a1",
                requests.single { it.method == HttpMethod.Post }.url.toString(),
            )
            assertEquals(listOf("server", "certificate"), coolify.accountSettings.map { it.key })
        }

    @Test
    fun serversAreReadAsTyped() {
        assertEquals(
            SelfHostedProvider.Server("pve.lan", 8006),
            SelfHostedProvider.Server.parse(" https://PVE.lan:8006/ "),
        )
        assertEquals(SelfHostedProvider.Server("192.168.1.5", 443), SelfHostedProvider.Server.parse("192.168.1.5"))
        assertEquals(SelfHostedProvider.Server("nas", 443), SelfHostedProvider.Server.parse("nas"))
        assertNull(SelfHostedProvider.Server.parse("http://host:99999"))
        assertNull(SelfHostedProvider.Server.parse("host/path"))
        assertNull(SelfHostedProvider.Server.parse(null))
    }

    @Test
    fun anotherHostIsNeverCalled() =
        runTest {
            val error = provider("proxmox").fetchSnapshot(account("proxmox", "evil.example/../x")).exceptionOrNull()

            assertTrue(error != null)
            assertTrue(requests.isEmpty())
        }
}
