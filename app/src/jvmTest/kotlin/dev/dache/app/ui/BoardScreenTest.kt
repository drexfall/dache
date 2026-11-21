package dev.dache.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import dev.dache.app.boards.BoardActions
import dev.dache.app.boards.BoardScreen
import dev.dache.app.navigation.AppNavigator
import dev.dache.app.navigation.LocalAppNavigator
import dev.dache.core.boards.Board
import dev.dache.core.boards.BoardIcon
import dev.dache.core.boards.Datapoint
import dev.dache.core.boards.Widget
import dev.dache.core.boards.WidgetChart
import dev.dache.core.dashboard.AccountResult
import dev.dache.core.dashboard.DashboardState
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.DeployStatus
import dev.dache.core.model.Deployment
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.ResourceRef
import dev.dache.core.model.Site
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

class BoardScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val account = ConnectedAccount("n1", ProviderId.NETLIFY, "acme")
    private val state =
        DashboardState(
            accounts = listOf(account),
            results =
                listOf(
                    AccountResult(
                        account,
                        ProviderSnapshot(
                            sites =
                                listOf(
                                    Site(
                                        account.providerId,
                                        account.id,
                                        "s1",
                                        "https://x",
                                        "shop",
                                        "https://shop.example.app",
                                        emptyList(),
                                        null,
                                        listOf(
                                            Deployment(
                                                "d1",
                                                "https://x",
                                                DeployStatus.FAILED,
                                                "main",
                                                null,
                                                Clock.System.now(),
                                                9,
                                            ),
                                        ),
                                    ),
                                ),
                        ),
                        error = null,
                    ),
                ),
            lastUpdated = Clock.System.now(),
        )

    /** Remembers what the screen asked for. */
    private class Harness : BoardActions {
        val opened = mutableListOf<ResourceRef>()
        val edits = mutableListOf<String?>()

        var name: String? = null
        var savedIcon: BoardIcon? = null

        override fun rename(name: String) {
            this.name = name
        }

        override fun setIcon(icon: BoardIcon) {
            savedIcon = icon
        }

        override fun delete() = Unit

        override fun saveWidgets(widgets: List<Widget>) = Unit

        override fun newWidgetId() = "new"

        val navigator =
            object : AppNavigator {
                override fun openAccount(accountId: String) = Unit

                override fun openResource(ref: ResourceRef) {
                    opened += ref
                }

                override fun openManageAccounts() = Unit
            }
    }

    private fun show(
        board: Board,
        dashboard: DashboardState = state,
        canDelete: Boolean = true,
        onConnectAccount: (() -> Unit)? = null,
    ): Harness {
        val harness = Harness()
        rule.setContent {
            TestHost {
                CompositionLocalProvider(LocalAppNavigator provides harness.navigator) {
                    BoardScreen(
                        board,
                        dashboard,
                        harness,
                        onEdit = { harness.edits += it },
                        onRefresh = {},
                        onOpenMenu = null,
                        canDelete = canDelete,
                        onConnectAccount = onConnectAccount,
                    )
                }
            }
        }
        return harness
    }

    @Test
    fun anEmptyBoardOffersTheEditor() {
        val harness = show(Board("b", "Ops", "default"))

        rule.onNodeWithText("Edit board").performClick()
        rule.waitForIdle()
        assertEquals(listOf<String?>(null), harness.edits)
    }

    @Test
    fun theLastBoardCantBeDeleted() {
        show(Board("b", "Ops", "default"), canDelete = false)

        rule.onNodeWithContentDescription("More board actions").performClick()
        rule.onNodeWithText("Delete board").assertIsNotEnabled()
    }

    @Test
    fun theBoardsNameAndIconAreChangedTogether() {
        val harness = show(Board("b", "Ops", "default", icon = BoardIcon.ofSymbol("bell")))

        rule.onNodeWithContentDescription("More board actions").performClick()
        rule.onNodeWithText("Name and icon").performClick()
        rule.onNodeWithContentDescription("Bell").assertIsSelected()
        rule.onNode(hasSetTextAction()).performTextReplacement("Releases")
        rule.onNodeWithContentDescription("Star").performClick()
        rule.onNodeWithText("Save").performClick()
        rule.waitForIdle()

        assertEquals("Releases", harness.name)
        assertEquals(BoardIcon.ofSymbol("star"), harness.savedIcon)
    }

    @Test
    fun withNoAccountsTheBoardAsksForOne() {
        var connects = 0
        show(Board("b", "Overview", "default"), DashboardState(), onConnectAccount = { connects++ })

        rule.onNodeWithText("Connect an account").performClick()
        rule.waitForIdle()
        assertEquals(1, connects)
    }

    @Test
    fun listItemsOpenTheirResourceAndALongPressEditsTheWidget() {
        val harness = show(Board("b", "Ops", "default", listOf(Widget("s", Datapoint.SITES, WidgetChart.LIST))))

        rule.onNodeWithText("shop").performClick()
        rule.waitForIdle()
        assertEquals(listOf("s1"), harness.opened.map { it.nativeId })

        rule.onNodeWithText("Sites").performTouchInput { longClick() }
        rule.waitForIdle()
        assertEquals(listOf<String?>("s"), harness.edits)

        rule.onNodeWithContentDescription("Edit").performClick()
        rule.waitForIdle()
        assertEquals(listOf("s", null), harness.edits)
    }
}
