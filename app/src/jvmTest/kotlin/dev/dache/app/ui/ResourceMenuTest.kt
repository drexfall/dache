package dev.dache.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import dev.dache.app.actions.ActionHost
import dev.dache.app.actions.LocalActionHost
import dev.dache.app.dashboard.SiteRow
import dev.dache.app.ui.components.LinkOpener
import dev.dache.app.ui.components.LocalLinkOpener
import dev.dache.core.dashboard.DashboardState
import dev.dache.core.model.ActionInfo
import dev.dache.core.model.ActionTarget
import dev.dache.core.model.ProviderId
import dev.dache.core.model.Resource
import dev.dache.core.model.ResourceKind
import dev.dache.core.model.Site
import org.junit.Rule
import org.junit.Test
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class ResourceMenuTest {
    @get:Rule
    val rule = createComposeRule()

    private val site =
        Site(
            ProviderId.NETLIFY,
            "n1",
            "s1",
            webUrl = "https://app.netlify.com/sites/shop",
            name = "shop",
            primaryUrl = "https://shop.example.app",
            customDomains = emptyList(),
            repoUrl = null,
            deployments = emptyList(),
        )
    private val redeploy =
        ActionInfo(
            id = "redeploy",
            label = "Redeploy",
            kind = ResourceKind.SITE,
            onDeployments = false,
            destructive = false,
            description = null,
            inputs = emptyList(),
            permission = null,
        )

    private val opened = mutableListOf<String>()
    private val copied = mutableListOf<String>()
    private val started = mutableListOf<Pair<String, ActionTarget>>()

    private val clipboard =
        object : Clipboard {
            override suspend fun getClipEntry(): ClipEntry? = null

            override suspend fun setClipEntry(clipEntry: ClipEntry?) {
                val selection = clipEntry?.nativeClipEntry as Transferable
                copied += selection.getTransferData(DataFlavor.stringFlavor) as String
            }

            override val nativeClipboard get() = error("not used")
        }

    private val actions =
        object : ActionHost {
            override fun actionsFor(
                resource: Resource,
                onDeployments: Boolean,
            ) = if (onDeployments) emptyList() else listOf(redeploy)

            override fun start(
                resource: Resource,
                action: ActionInfo,
                target: ActionTarget,
                targetName: String,
            ) {
                started += action.id to target
            }
        }

    private fun show() {
        rule.setContent {
            TestHost {
                CompositionLocalProvider(
                    LocalLinkOpener provides LinkOpener { opened += it },
                    LocalClipboard provides clipboard,
                    LocalActionHost provides actions,
                ) {
                    SiteRow(site, DashboardState())
                }
            }
        }
    }

    private fun choose(entry: String) {
        rule.onNodeWithText("shop").performMouseInput { rightClick() }
        rule.onNodeWithText(entry).performClick()
        rule.waitForIdle()
    }

    @Test
    fun opensTheSiteInTheProviderAndAtItsAddress() {
        show()
        choose("Open in Netlify")
        choose("Visit shop.example.app")
        assertEquals(listOf(site.webUrl, site.primaryUrl), opened)
    }

    @Test
    fun copiesTheLinkTheAddressAndTheName() {
        show()
        choose("Copy Netlify link")
        choose("Copy address")
        choose("Copy name")
        assertEquals(listOf(site.webUrl, site.primaryUrl, "shop"), copied)
    }

    @Test
    fun showsTheDetailsAndHidesThemAgain() {
        show()
        choose("Show details")
        rule.onNodeWithText("Open in Netlify").assertExists()
        choose("Hide details")
        rule.onNodeWithText("Open in Netlify").assertDoesNotExist()
    }

    @Test
    fun startsTheProvidersActionsOnTheResource() {
        show()
        choose("Redeploy")
        assertEquals(listOf("redeploy" to ActionTarget(site.ref)), started)
    }
}
