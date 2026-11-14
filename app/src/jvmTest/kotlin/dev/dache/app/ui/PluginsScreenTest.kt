package dev.dache.app.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.dache.app.plugins.PluginsScreen
import dev.dache.app.plugins.PluginsViewModel
import dev.dache.core.provider.ProviderCatalog
import dev.dache.core.provider.mock.MockProvider
import dev.dache.plugin.registry.BundledPlugins
import dev.dache.plugin.registry.PluginManager
import dev.dache.plugin.registry.RegistryClient
import dev.dache.plugin.registry.RegistryStatus
import dev.dache.plugin.runtime.providerId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test

class PluginsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun aFailingRegistryIsOneLineAndBundledPluginsStayListed() {
        val registry = RegistryClient(offlineClient())
        val manager =
            PluginManager(
                store = InMemoryPluginStore(),
                catalog = ProviderCatalog(),
                registry = registry,
                bundled = BundledPlugins.manifests,
                providerFor = { MockProvider(it.providerId(), dev.dache.app.InMemoryTokenVault()) },
            ).also { it.start(CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
        lateinit var viewModel: PluginsViewModel
        rule.setContent {
            TestHost {
                viewModel = remember { PluginsViewModel(manager, registry) }
                val state by viewModel.state.collectAsState()
                PluginsScreen(viewModel, state, onOpenMenu = null)
            }
        }
        rule.waitUntil(10_000) { manager.registryStatus.value is RegistryStatus.Failed }

        rule.onNodeWithText("Registry unreachable", substring = true).assertExists()
        rule.onNodeWithText("404", substring = true).assertDoesNotExist()
        rule.onNodeWithText("In use").assertExists()
        rule.onNodeWithText(BundledPlugins.manifests.minBy { it.name.lowercase() }.name).assertExists()
    }
}
