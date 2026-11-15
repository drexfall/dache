package dev.dache.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import dev.dache.app.boards.BoardActions
import dev.dache.app.boards.BoardScreen
import dev.dache.app.navigation.AppNavigator
import dev.dache.app.navigation.LocalAppNavigator
import dev.dache.core.boards.Board
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

    /** Applies actions the way the repository would, and remembers what was opened. */
    private inner class Harness(
        initial: Board,
    ) : BoardActions {
        var board by mutableStateOf(initial)
        val opened = mutableListOf<ResourceRef>()

        override fun rename(name: String) {
            board = board.copy(name = name)
        }

        override fun delete() = Unit

        override fun putWidget(widget: Widget) {
            board = board.withWidget(widget)
        }

        override fun removeWidget(id: String) {
            board = board.withoutWidget(id)
        }

        override fun moveWidget(
            id: String,
            by: Int,
        ) {
            board = board.moved(id, by)
        }

        override fun newWidgetId() = "new-${board.widgets.size}"

        val navigator =
            object : AppNavigator {
                override fun openAccount(accountId: String) = Unit

                override fun openResource(ref: ResourceRef) {
                    opened += ref
                }

                override fun openManageAccounts() = Unit
            }
    }

    private fun show(board: Board): Harness {
        val harness = Harness(board)
        rule.setContent {
            TestHost {
                CompositionLocalProvider(LocalAppNavigator provides harness.navigator) {
                    BoardScreen(harness.board, state, harness, onRefresh = {}, onOpenMenu = null)
                }
            }
        }
        return harness
    }

    @Test
    fun anEmptyBoardAddsAWidgetChosenInTheEditor() {
        val harness = show(Board("b", "Ops", "default"))

        rule.onNodeWithText("Add widget").performClick()
        // The data dropdown; the preview below shows the same name as its title.
        rule.onAllNodesWithText("Deploys")[0].performClick()
        rule.onNodeWithText("Failing sites").performClick()
        rule.onNodeWithText("List").performClick()
        // The preview already shows the user's own failing site.
        rule.onNodeWithText("shop").assertExists()
        rule.onNodeWithText("Add").performClick()
        rule.waitForIdle()

        val widget = harness.board.widgets.single()
        assertEquals(Datapoint.FAILING_SITES, widget.datapoint)
        assertEquals(WidgetChart.LIST, widget.chart)
    }

    @Test
    fun editModeMovesResizesAndRemovesWidgets() {
        val harness =
            show(
                Board(
                    "b",
                    "Ops",
                    "default",
                    listOf(
                        Widget("a", Datapoint.DEPLOYS, title = "Alpha"),
                        Widget("b", Datapoint.SITES, title = "Beta"),
                    ),
                ),
            )
        rule.onNodeWithContentDescription("Edit").performClick()

        rule.onNodeWithContentDescription("Move Alpha earlier").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Move Alpha later").performClick()
        rule.waitForIdle()
        assertEquals(listOf("b", "a"), harness.board.widgets.map { it.id })

        rule.onNodeWithContentDescription("Make Beta wider").performClick()
        rule.waitForIdle()
        assertEquals(2, harness.board.widget("b")?.span)

        rule.onNodeWithContentDescription("Remove Alpha").performClick()
        rule.waitForIdle()
        assertEquals(listOf("b"), harness.board.widgets.map { it.id })
    }

    @Test
    fun aLongPressStartsEditingAndListItemsOpenTheirResource() {
        val harness = show(Board("b", "Ops", "default", listOf(Widget("s", Datapoint.SITES, WidgetChart.LIST))))

        rule.onNodeWithText("shop").performClick()
        rule.waitForIdle()
        assertEquals(listOf("s1"), harness.opened.map { it.nativeId })

        rule.onNodeWithText("Sites").performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Remove Sites").assertExists()
    }
}
