package dev.dache.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import dev.dache.app.boards.BoardActions
import dev.dache.app.boards.BoardEditorScreen
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

@OptIn(ExperimentalTestApi::class)
class BoardEditorTest {
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

    /** Remembers every save, the way the repository would apply it. */
    private class Harness : BoardActions {
        var saved: List<Widget>? = null
        var name: String? = null
        private var ids = 0

        override fun rename(name: String) {
            this.name = name
        }

        override fun delete() = Unit

        override fun saveWidgets(widgets: List<Widget>) {
            saved = widgets
        }

        override fun newWidgetId() = "new-${ids++}"

        fun order() = saved.orEmpty().map { it.id }
    }

    private val alpha = Widget("a", Datapoint.DEPLOYS, WidgetChart.STAT, title = "Alpha")
    private val beta = Widget("b", Datapoint.FAILED_DEPLOYS, WidgetChart.STAT, title = "Beta")

    private fun show(
        widgets: List<Widget>,
        selected: String? = null,
    ): Harness {
        val harness = Harness()
        rule.setContent {
            TestHost {
                CompositionLocalProvider(LocalAppNavigator provides NoNavigation) {
                    BoardEditorScreen(Board("b", "Ops", "default", widgets), state, harness, selected, onClose = {})
                }
            }
        }
        return harness
    }

    /** Nothing in the editor should navigate; a list row pressed there selects its widget. */
    private object NoNavigation : AppNavigator {
        override fun openAccount(accountId: String) = error("the editor opened an account")

        override fun openResource(ref: ResourceRef) = error("the editor opened a resource")

        override fun openManageAccounts() = error("the editor opened account settings")
    }

    private fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot

    @Test
    fun tappingALibraryWidgetAddsAndSelectsIt() {
        val harness = show(emptyList())
        rule.onNodeWithText("Dashboard").assertExists()

        rule.onNodeWithContentDescription("Add Failing sites as List").performClick()
        rule.waitForIdle()

        val widget = harness.saved.orEmpty().single()
        assertEquals(Datapoint.FAILING_SITES, widget.datapoint)
        assertEquals(WidgetChart.LIST, widget.chart)
        // Live on the canvas, and selected, so its properties are showing.
        rule.onNodeWithText("shop").assertExists().performClick()
        rule.onNodeWithContentDescription("Failing sites").assertIsSelected()
        rule.onNodeWithText("Show as").assertExists()
    }

    @Test
    fun propertiesChangeTheSelectedWidget() {
        val harness = show(listOf(alpha, beta), selected = "a")

        rule.onNodeWithContentDescription("Move Alpha earlier").assertIsNotEnabled()
        rule.onNodeWithText("Bars").performClick()
        rule.onNodeWithContentDescription("Make Alpha wider").performClick()
        rule.onNodeWithContentDescription("Make Alpha taller").performClick()
        rule.onNodeWithText("30 days").performClick()
        rule.onNodeWithText("All Netlify accounts").performClick()
        rule.waitForIdle()

        val changed = harness.saved.orEmpty().first { it.id == "a" }
        assertEquals(WidgetChart.BARS, changed.chart)
        assertEquals(2, changed.span)
        assertEquals(3, changed.effectiveHeight, "one taller than a bar chart's two rows")
        assertEquals(30, changed.days)
        assertEquals("netlify", changed.scope.providerId)

        rule.onNodeWithContentDescription("Move Alpha later").performClick()
        rule.waitForIdle()
        assertEquals(listOf("b", "a"), harness.order())

        rule.onNodeWithContentDescription("Remove Alpha").performClick()
        rule.waitForIdle()
        assertEquals(listOf("b"), harness.order())
        rule.onNodeWithText("Dashboard").assertExists()
    }

    @Test
    fun aLibraryWidgetDraggedWithTheMouseLandsWhereItsDropped() {
        val harness = show(listOf(alpha, beta))
        val tile = rule.onNodeWithContentDescription("Add Sites as List").bounds()
        val target = rule.onNodeWithContentDescription("Beta").bounds()

        rule.onRoot().performMouseInput {
            moveTo(tile.center)
            press()
            moveTo(tile.center + Offset(40f, 0f))
            moveTo(target.center)
            release()
        }
        rule.waitForIdle()

        assertEquals(listOf("a", "new-0", "b"), harness.order(), "dropped on Beta: takes its place")
        assertEquals(Datapoint.SITES, harness.saved.orEmpty()[1].datapoint)
    }

    @Test
    fun aLongPressDragsAWidgetToANewPlace() {
        val harness = show(listOf(alpha, beta))
        val from = rule.onNodeWithContentDescription("Alpha").bounds()
        val to = rule.onNodeWithContentDescription("Beta").bounds()

        rule.onRoot().performTouchInput {
            down(from.center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveTo(from.center + Offset(10f, 0f))
            moveTo(to.center)
            up()
        }
        rule.waitForIdle()

        assertEquals(listOf("b", "a"), harness.order())
    }

    @Test
    fun theSelectedWidgetsEdgeHandleResizesIt() {
        val harness = show(listOf(alpha, beta), selected = "a")
        val widget = rule.onNodeWithContentDescription("Alpha").bounds()
        val edge = Offset(widget.right, widget.center.y)

        rule.onRoot().performMouseInput {
            moveTo(edge)
            press()
            moveTo(edge + Offset(widget.width / 2, 0f))
            moveTo(edge + Offset(widget.width * 1.1f, widget.height * 1.2f))
            release()
        }
        rule.waitForIdle()

        val resized = harness.saved.orEmpty().first { it.id == "a" }
        assertEquals(2, resized.span, "a column wider")
        assertEquals(1, resized.effectiveHeight, "the right edge only changes the width")
    }
}
