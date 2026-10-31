package dev.dache.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import dev.dache.app.account.AccountScreen
import dev.dache.app.navigation.AppNavigator
import dev.dache.app.navigation.LocalAppNavigator
import dev.dache.app.overview.OverviewScreen
import dev.dache.app.ui.components.LinkOpener
import dev.dache.app.ui.components.LocalLinkOpener
import dev.dache.app.ui.layout.FormFactor
import dev.dache.app.ui.layout.LocalFormFactor
import dev.dache.app.ui.theme.DacheTheme
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ResourceRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test

/**
 * Renders the overview and each account page with mock data to PNGs, light and dark, for reviewing the UI
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
                shot(File(dir, "overview-$name-$mode.png"), width, dark, form) {
                    OverviewScreen(state, onRefresh = {}, onOpenMenu = null, onConnectAccount = {})
                }
                state.accounts.forEach { account ->
                    shot(File(dir, "${account.providerId.id}-$name-$mode.png"), width, dark, form) {
                        AccountScreen(account, state, onRefresh = {}, onOpenMenu = null, onNavigate = {})
                    }
                }
            }
        }
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
