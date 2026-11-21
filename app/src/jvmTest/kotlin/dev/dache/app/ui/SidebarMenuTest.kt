package dev.dache.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import dev.dache.app.navigation.Destination
import dev.dache.app.navigation.Sidebar
import dev.dache.app.ui.components.LinkOpener
import dev.dache.app.ui.components.LocalLinkOpener
import dev.dache.core.boards.Board
import dev.dache.core.dashboard.DashboardState
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderId
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SidebarMenuTest {
    @get:Rule
    val rule = createComposeRule()

    private val account = ConnectedAccount("n1", ProviderId.NETLIFY, "acme")
    private val selected = mutableListOf<Destination>()
    private val opened = mutableListOf<String>()

    private fun show() {
        rule.setContent {
            TestHost {
                CompositionLocalProvider(LocalLinkOpener provides LinkOpener { opened += it }) {
                    Sidebar(
                        state = DashboardState(accounts = listOf(account)),
                        current = Destination.Board("home"),
                        onSelect = { selected += it },
                        onAddAccount = {},
                        boards = listOf(Board("home", "Overview", "default"), Board("ops", "Ops", "default")),
                    )
                }
            }
        }
    }

    private fun choose(
        entry: String,
        on: String,
    ) {
        rule.onNodeWithText(on).performMouseInput { rightClick() }
        rule.onNodeWithText(entry).performClick()
        rule.waitForIdle()
    }

    @Test
    fun aBoardOpensOrOpensInTheEditor() {
        show()
        choose("Open", on = "Ops")
        choose("Edit board", on = "Ops")
        assertEquals(
            listOf(Destination.Board("ops"), Destination.Board("ops"), Destination.BoardEditor("ops")),
            selected,
        )
    }

    @Test
    fun anAccountOpensItsPageItsSettingsOrTheProvidersDashboard() {
        show()
        choose("Open", on = "acme")
        choose("Account settings", on = "acme")
        choose("Open in Netlify", on = "acme")
        assertEquals(listOf(Destination.Account("n1"), Destination.AccountSettings("n1")), selected)
        assertEquals(listOf("https://app.netlify.com"), opened)
    }
}
