package dev.dache.app.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.dache.app.plugins.ConsentDialog
import dev.dache.plugin.registry.BundledPlugins
import dev.dache.plugin.registry.PendingInstall
import dev.dache.plugin.registry.Plugin
import dev.dache.plugin.registry.PluginSource
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ConsentDialogTest {
    @get:Rule
    val rule = createComposeRule()

    private val netlify = BundledPlugins.manifests.first { it.id == "netlify" }

    @Test
    fun anUpdateListsItsHostsAndMarksTheNewOne() {
        val update = netlify.copy(version = "2.0.0", hosts = netlify.hosts + "uploads.netlify.com")
        val pending = PendingInstall(update, "", "", PluginSource.REGISTRY, Plugin(netlify, PluginSource.BUNDLED))
        var confirmed = 0
        rule.setContent { TestHost { ConsentDialog(pending, onConfirm = { confirmed++ }, onDismiss = {}) } }

        rule.onNodeWithText("Update Netlify to 2.0.0?").assertExists()
        rule.onNodeWithText("Verified").assertExists()
        netlify.hosts.forEach { rule.onNodeWithText(it).assertExists() }
        rule.onNodeWithText("uploads.netlify.com (new)").assertExists()
        rule.onNodeWithText("Update").assertIsEnabled().performClick()

        assertEquals(1, confirmed)
    }

    @Test
    fun anUpdateThatMovesSavedTokensNamesTheAccountsAndNeedsATick() {
        val update = netlify.copy(version = "2.0.0", hosts = netlify.hosts + "collect.example.net")
        val pending = PendingInstall(update, "", "", PluginSource.REGISTRY, Plugin(netlify, PluginSource.BUNDLED))
        var confirmed = 0
        rule.setContent {
            TestHost {
                ConsentDialog(
                    pending,
                    onConfirm = { confirmed++ },
                    onDismiss = {},
                    affectedAccounts = listOf("Work", "Personal"),
                )
            }
        }

        rule.onNodeWithText("Already connected").assertExists()
        rule.onNodeWithText("Work").assertExists()
        rule.onNodeWithText("Personal").assertExists()
        rule.onNodeWithText("Update").assertIsNotEnabled().performClick()
        assertEquals(0, confirmed)

        rule.onNodeWithText("Send these accounts’ tokens to this plugin").performClick()
        rule.onNodeWithText("Update").assertIsEnabled().performClick()

        assertEquals(1, confirmed)
    }

    @Test
    fun aSideloadIsMarkedUnverifiedAndNeedsTheExtraTick() {
        val sideload = netlify.copy(id = "acme", name = "Acme Cloud", publisher = null, hosts = listOf("api.acme.dev"))
        val pending = PendingInstall(sideload, "", "", PluginSource.SIDELOADED, null)
        var confirmed = 0
        rule.setContent { TestHost { ConsentDialog(pending, onConfirm = { confirmed++ }, onDismiss = {}) } }

        rule.onNodeWithText("Install Acme Cloud ${sideload.version}?").assertExists()
        rule.onNodeWithText("Unverified").assertExists()
        rule.onNodeWithText("api.acme.dev").assertExists()
        rule.onNodeWithText("Install").assertIsNotEnabled()

        rule.onNodeWithText("I trust this plugin with the token I give it").performClick()
        rule.onNodeWithText("Install").assertIsEnabled().performClick()

        assertEquals(1, confirmed)
    }
}
