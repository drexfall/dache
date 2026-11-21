package dev.dache.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import dev.dache.app.account.AccountScreen
import dev.dache.app.boards.BoardActions
import dev.dache.app.boards.BoardEditorScreen
import dev.dache.app.boards.BoardScreen
import dev.dache.app.navigation.AppNavigator
import dev.dache.app.navigation.LocalAppNavigator
import dev.dache.app.plugins.ConsentDialog
import dev.dache.app.plugins.PluginsScreen
import dev.dache.app.plugins.PluginsViewModel
import dev.dache.app.ui.components.LinkOpener
import dev.dache.app.ui.components.LocalLinkOpener
import dev.dache.app.ui.layout.FormFactor
import dev.dache.app.ui.layout.LocalFormFactor
import dev.dache.app.ui.theme.DacheTheme
import dev.dache.core.boards.Board
import dev.dache.core.boards.BoardIcon
import dev.dache.core.boards.BoardRepository
import dev.dache.core.boards.Datapoint
import dev.dache.core.boards.Widget
import dev.dache.core.boards.WidgetChart
import dev.dache.core.boards.WidgetScope
import dev.dache.core.boards.defaultBoard
import dev.dache.core.dashboard.DashboardState
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ResourceRef
import dev.dache.core.provider.ProviderCatalog
import dev.dache.core.provider.mock.MockProvider
import dev.dache.plugin.registry.BundledPlugins
import dev.dache.plugin.registry.PendingInstall
import dev.dache.plugin.registry.Plugin
import dev.dache.plugin.registry.PluginManager
import dev.dache.plugin.registry.PluginSource
import dev.dache.plugin.registry.PluginState
import dev.dache.plugin.registry.PluginStore
import dev.dache.plugin.registry.RegistryClient
import dev.dache.plugin.runtime.http.createPluginHttpClient
import dev.dache.plugin.runtime.providerId
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test

/**
 * Renders the default board and each account page with mock data to PNGs, light and dark, for reviewing the UI
 * without tokens. Runs only when `DACHE_RENDER_DIR` names an output directory:
 * `DACHE_RENDER_DIR=/tmp/shots ./gradlew :app:jvmTest --tests '*RenderScreens*'`.
 */
class RenderScreens {
    @Test
    fun render() {
        val dir = System.getenv("DACHE_RENDER_DIR")?.let(::File) ?: return
        dir.mkdirs()
        val graph = TestGraph(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val state =
            runBlocking {
                ProviderId.entries.forEach { graph.accounts.connect(it, "mock-token") }
                graph.dashboard.refresh()
                graph.dashboard.state.first { it.lastUpdated != null && it.results.size == ProviderId.entries.size }
            }
        for (dark in listOf(false, true)) {
            val mode = if (dark) "dark" else "light"
            for ((name, width) in listOf("desktop" to DESKTOP_WIDTH, "phone" to PHONE_WIDTH)) {
                val form = if (width == DESKTOP_WIDTH) FormFactor.EXPANDED else FormFactor.COMPACT
                shot(File(dir, "home-$name-$mode.png"), width, dark, form) {
                    BoardScreen(homeBoard, state, NoBoardActions, onEdit = {}, onRefresh = {}, onOpenMenu = null)
                }
                shot(File(dir, "plugins-$name-$mode.png"), width, dark, form) {
                    val viewModel = remember { PluginsViewModel(plugins, registry) }
                    val pluginsState by viewModel.state.collectAsState()
                    PluginsScreen(viewModel, pluginsState, onOpenMenu = null)
                }
                for ((label, pending) in consents()) {
                    shot(File(dir, "consent-$label-$name-$mode.png"), width, dark, form) {
                        ConsentDialog(pending, onConfirm = {}, onDismiss = {})
                    }
                }
                boardShots(dir, "$name-$mode", width, dark, form, state)
                state.accounts.forEach { account ->
                    shot(File(dir, "${account.providerId.id}-$name-$mode.png"), width, dark, form) {
                        AccountScreen(account, state, onRefresh = {}, onOpenMenu = null, onNavigate = {})
                    }
                }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** A sample board, and its editor with a widget selected. */
    private fun boardShots(
        dir: File,
        suffix: String,
        width: Int,
        dark: Boolean,
        form: FormFactor,
        state: DashboardState,
    ) {
        shot(File(dir, "board-$suffix.png"), width, dark, form) {
            BoardScreen(sampleBoard, state, NoBoardActions, onEdit = {}, onRefresh = {}, onOpenMenu = null)
        }
        shot(File(dir, "board-editor-$suffix.png"), width, dark, form) {
            BoardEditorScreen(sampleBoard, state, NoBoardActions, initialSelection = "w6", onClose = {})
        }
        shot(File(dir, "board-editor-icon-$suffix.png"), width, dark, form) {
            BoardEditorScreen(sampleBoard, state, NoBoardActions, initialSelection = null, onClose = {})
        }
    }

    private val homeBoard =
        generateSequence(0) { it + 1 }.iterator().let { ids ->
            defaultBoard("home", BoardRepository.DEFAULT_NAME, "default") { "h${ids.next()}" }
        }

    private val sampleBoard =
        Board(
            "board",
            "Release health",
            "default",
            listOf(
                Widget("w1", Datapoint.DEPLOYS, WidgetChart.STAT),
                Widget("w2", Datapoint.FAILED_DEPLOYS, WidgetChart.STAT),
                Widget("w3", Datapoint.MONITORS_DOWN, WidgetChart.STAT),
                Widget("w4", Datapoint.DAILY_COST, WidgetChart.STAT, scope = WidgetScope(providerId = "digitalocean")),
                Widget("w5", Datapoint.DEPLOYS, WidgetChart.BARS, days = 30, span = 2),
                Widget("w6", Datapoint.RESPONSE_TIME, WidgetChart.LINE, span = 2),
                Widget("w7", Datapoint.SITES, WidgetChart.LIST, span = 2),
                Widget("w8", Datapoint.MONITORS, WidgetChart.LIST, span = 2),
                Widget("w9", Datapoint.UPTIME, WidgetChart.STRIP, span = 2),
            ),
            icon = BoardIcon.ofSymbol("check"),
        )

    private object NoBoardActions : BoardActions {
        override fun rename(name: String) = Unit

        override fun setIcon(icon: BoardIcon) = Unit

        override fun delete() = Unit

        override fun saveWidgets(widgets: List<Widget>) = Unit

        override fun newWidgetId() = "new"
    }

    // Screenshots never touch the network: the registry answers 404, so the plugin screen shows its unreachable state.
    private val registry =
        RegistryClient(createPluginHttpClient(MockEngine { respondError(HttpStatusCode.NotFound) }))

    private val plugins =
        PluginManager(
            store =
                object : PluginStore {
                    override val state = MutableStateFlow(PluginState())

                    override suspend fun update(transform: (PluginState) -> PluginState) = state.update(transform)
                },
            catalog = ProviderCatalog(),
            registry = registry,
            bundled = BundledPlugins.manifests,
            providerFor = { MockProvider(it.providerId(), InMemoryTokenVault()) },
        ).also {
            runBlocking {
                it.start(this@RenderScreens.scope)
                it.checkRegistry()
            }
        }

    /** A verified update that adds a host, and an unverified sideload. */
    private fun consents(): List<Pair<String, PendingInstall>> {
        val netlify = BundledPlugins.manifests.first { it.id == "netlify" }
        val current = Plugin(netlify, PluginSource.BUNDLED)
        val update = netlify.copy(version = "2.0.0", hosts = netlify.hosts + "uploads.netlify.com")
        val sideload = netlify.copy(id = "acme", name = "Acme Cloud", publisher = null, hosts = listOf("api.acme.dev"))
        return listOf(
            "update" to PendingInstall(update, "", "", PluginSource.REGISTRY, current),
            "sideload" to PendingInstall(sideload, "", "", PluginSource.SIDELOADED, null),
        )
    }

    private fun shot(
        file: File,
        width: Int,
        dark: Boolean,
        form: FormFactor,
        content: @Composable () -> Unit,
    ) {
        ImageComposeScene(width, HEIGHT, Density(1f)) {
            DacheTheme(dark = dark) {
                CompositionLocalProvider(
                    LocalFormFactor provides form,
                    LocalAppNavigator provides NoNavigation,
                    LocalLinkOpener provides LinkOpener {},
                ) {
                    content()
                }
            }
        }.use { scene ->
            // Two frames: the first lays out, the second draws what measured state settled on.
            scene.render(0)
            val image = scene.render(FRAME_NANOS)
            file.writeBytes(requireNotNull(image.encodeToData()).bytes)
        }
    }

    private object NoNavigation : AppNavigator {
        override fun openAccount(accountId: String) = Unit

        override fun openResource(ref: ResourceRef) = Unit

        override fun openManageAccounts() = Unit
    }

    private companion object {
        const val DESKTOP_WIDTH = 1200
        const val PHONE_WIDTH = 412
        const val HEIGHT = 2400
        const val FRAME_NANOS = 500_000_000L
    }
}
