package dev.dache.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.dache.app.DacheApp
import dev.dache.app.navigation.BackRequests
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderId
import org.junit.Rule
import org.junit.Test

/** The whole app on the desktop layout: pages open from the sidebar and from each other, and back retraces them. */
class BackNavigationTest {
    @get:Rule
    val rule = createComposeRule()

    private val back = BackRequests()

    private fun count(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun hasDescription(text: String) =
        rule.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty()

    /** Home is the only page where its board's name, "Overview", shows twice: in the sidebar and as the title. */
    private fun onHome() = count("Overview") == 2

    @Test
    fun backRetracesHomeAccountSettingsAndTheTopLevelPages() {
        val container = testContainer(listOf(ConnectedAccount("acc-1", ProviderId.NETLIFY, "acme")))
        rule.setContent { TestHost { DacheApp(container, backRequests = back) } }
        rule.waitUntil(TIMEOUT) { onHome() }

        // Sidebar: the account's page, then its settings from the toolbar.
        rule.onAllNodesWithText("Netlify")[0].performClick()
        rule.waitUntil(TIMEOUT) { hasDescription("Account settings") }
        rule.onNodeWithContentDescription("Account settings").performClick()
        rule.waitUntil(TIMEOUT) { count("Settings") >= 1 && !hasDescription("Account settings") }

        // Back: settings -> account -> home.
        back.request()
        rule.waitUntil(TIMEOUT) { hasDescription("Account settings") }
        back.request()
        rule.waitUntil(TIMEOUT) { onHome() }

        // Top-level pages stack too, and a page already open is gone back to rather than pushed again.
        rule.onNodeWithText("Manage accounts").performClick()
        rule.waitUntil(TIMEOUT) { count("Manage accounts") == 2 }
        rule.onAllNodesWithText("Plugins")[0].performClick()
        rule.waitUntil(TIMEOUT) { count("Plugins") == 2 }
        rule.onAllNodesWithText("Manage accounts")[0].performClick()
        rule.waitUntil(TIMEOUT) { count("Manage accounts") == 2 }
        back.request()
        rule.waitUntil(TIMEOUT) { onHome() }

        // With one page left, back changes nothing.
        back.request()
        rule.waitForIdle()
        rule.waitUntil(TIMEOUT) { onHome() }
    }

    private companion object {
        const val TIMEOUT = 15_000L
    }
}
