package dev.dache.plugin.registry

import dev.dache.core.model.Capability
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.provider.DevOpsProvider
import dev.dache.core.provider.ProviderCatalog
import dev.dache.plugin.api.PluginManifest
import dev.dache.plugin.runtime.http.createPluginHttpClient
import dev.dache.plugin.runtime.providerId
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PluginManagerTest {
    private val base = "https://registry.example/index"
    private var files = mapOf<String, String>()
    private val store = InMemoryPluginStore()
    private val catalog = ProviderCatalog(ready = false)

    private fun serve(
        index: String,
        signature: String,
    ) {
        files =
            mapOf(
                "$base/index.json" to index,
                "$base/index.json.sig" to signature,
                "$base/plugins/netlify/1.2.0.json" to RegistryFixtures.NETLIFY_1_2_0,
                "$base/plugins/netlify/2.0.0.json" to RegistryFixtures.NETLIFY_2_0_0,
                "$base/plugins/example/0.1.0.json" to RegistryFixtures.EXAMPLE_0_1_0,
            )
    }

    private val client =
        createPluginHttpClient(
            MockEngine { request ->
                files[request.url.toString()]?.let { respond(it) } ?: respondError(HttpStatusCode.NotFound)
            },
        )

    private fun TestScope.manager(): PluginManager =
        PluginManager(
            store = store,
            catalog = catalog,
            registry = RegistryClient(client, "$base/index.json", listOf(RegistryFixtures.PUBLIC_KEY)),
            bundled = BundledPlugins.manifests,
            providerFor = ::FakeProvider,
        ).also {
            it.start(backgroundScope)
            runCurrent()
        }

    @Test
    fun bundledPluginsFillTheCatalogOnceTheStoreIsRead() =
        runTest {
            val manager = manager()

            catalog.awaitReady()
            assertEquals(BundledPlugins.manifests.size, catalog.providers.value.size)
            assertTrue(manager.plugins.value.all { it.source == PluginSource.BUNDLED })
        }

    @Test
    fun updatesKeepingHostsAndAuthInstallWithoutAsking() =
        runTest {
            serve(RegistryFixtures.INDEX_10, RegistryFixtures.INDEX_10_SIG)
            val manager = manager()

            val index = manager.checkRegistry().getOrThrow()
            val waiting = manager.autoUpdate(index)
            runCurrent()

            assertTrue(waiting.isEmpty())
            val netlify = manager.plugins.value.single { it.id == "netlify" }
            assertEquals("1.2.0", netlify.manifest.version)
            assertEquals(PluginSource.REGISTRY, netlify.source)
            assertEquals(
                listOf("example"),
                manager.plugins.value
                    .missingFrom(index)
                    .map { it.id },
            )
            assertEquals(10, store.state.first().lastSerial)
        }

    @Test
    fun anUpdateThatAddsAHostWaitsForApproval() =
        runTest {
            serve(RegistryFixtures.INDEX_20, RegistryFixtures.INDEX_20_SIG)
            val manager = manager()

            val waiting = manager.autoUpdate(manager.checkRegistry().getOrThrow())
            runCurrent()

            assertEquals("2.0.0", waiting.single().entry.version)
            assertTrue(waiting.single().needsApproval)
            assertEquals(
                "1.1.0",
                manager.plugins.value
                    .single { it.id == "netlify" }
                    .manifest.version,
            )

            val pending = manager.prepare(waiting.single().entry).getOrThrow()
            assertTrue(pending.needsConsent)
            manager.install(pending)
            runCurrent()
            assertEquals(
                "2.0.0",
                manager.plugins.value
                    .single { it.id == "netlify" }
                    .manifest.version,
            )
        }

    @Test
    fun anIndexSignedByAnotherKeyIsRefused() =
        runTest {
            serve(RegistryFixtures.INDEX_10, RegistryFixtures.INDEX_20_SIG)
            val manager = manager()

            val error = manager.checkRegistry().exceptionOrNull()

            assertIs<RegistryException>(error)
            assertIs<RegistryStatus.Failed>(manager.registryStatus.value)
        }

    @Test
    fun anOlderIndexThanOneAlreadySeenIsRefused() =
        runTest {
            store.update { it.copy(lastSerial = 10) }
            serve(RegistryFixtures.INDEX_5, RegistryFixtures.INDEX_5_SIG)

            assertTrue(manager().checkRegistry().isFailure)
        }

    @Test
    fun aManifestThatDoesntMatchItsDigestIsRefused() =
        runTest {
            serve(RegistryFixtures.INDEX_10, RegistryFixtures.INDEX_10_SIG)
            val manager = manager()
            val index = manager.checkRegistry().getOrThrow()
            files =
                files + ("$base/plugins/example/0.1.0.json" to RegistryFixtures.EXAMPLE_0_1_0.replace("0.1.0", "0.1.1"))

            val error = manager.prepare(index.plugins.single { it.id == "example" }).exceptionOrNull()

            assertTrue(error?.message.orEmpty().contains("checksum"), error.toString())
        }

    @Test
    fun sideloadingNeedsTheOptInAndStaysUnverified() =
        runTest {
            val manager = manager()

            assertTrue(manager.prepareSideload(RegistryFixtures.EXAMPLE_0_1_0).isFailure)
            manager.setAllowSideload(true)
            val pending = manager.prepareSideload(RegistryFixtures.EXAMPLE_0_1_0).getOrThrow()
            assertTrue(pending.needsConsent)
            manager.install(pending)
            runCurrent()

            val example = assertNotNull(manager.plugins.value.firstOrNull { it.id == "example" })
            assertFalse(example.verified)
            assertNotNull(catalog[example.manifest.providerId()])
        }

    @Test
    fun removingAnOverrideBringsTheBundledPluginBack() =
        runTest {
            serve(RegistryFixtures.INDEX_10, RegistryFixtures.INDEX_10_SIG)
            val manager = manager()
            manager.autoUpdate(manager.checkRegistry().getOrThrow())
            runCurrent()

            manager.remove("netlify")
            runCurrent()

            val netlify = manager.plugins.value.single { it.id == "netlify" }
            assertEquals(PluginSource.BUNDLED, netlify.source)
        }

    @Test
    fun versionsCompareNumerically() {
        assertTrue(isNewer("1.10.0", "1.9.9"))
        assertFalse(isNewer("1.0.0", "1.0.0"))
        assertFalse(isNewer("1.0.0-beta", "0.9.0"))
    }
}

private class FakeProvider(
    manifest: PluginManifest,
) : DevOpsProvider {
    override val id = manifest.providerId()
    override val capabilities = emptySet<Capability>()

    override suspend fun validateCredentials(
        token: String,
        settings: Map<String, String>,
    ) = error("not used")

    override suspend fun fetchSnapshot(account: ConnectedAccount): Result<ProviderSnapshot> = error("not used")
}

private class InMemoryPluginStore : PluginStore {
    private val flow = MutableStateFlow(PluginState())
    override val state: StateFlow<PluginState> = flow

    override suspend fun update(transform: (PluginState) -> PluginState) = flow.update(transform)
}
