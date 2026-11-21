package dev.dache.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.unit.dp
import dev.dache.app.ui.components.ContextMenuArea
import dev.dache.app.ui.components.ContextMenuItem
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ContextMenuTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show() {
        rule.setContent {
            TestHost {
                // Stands in for the window, which goes back a page on Esc.
                Box(
                    Modifier.onKeyEvent {
                        if (it.key == Key.Escape) events += "back"
                        false
                    },
                ) {
                    ContextMenuArea(
                        listOf(
                            ContextMenuItem("Copy") { events += "copy" },
                            ContextMenuItem("Delete", enabled = false) { events += "delete" },
                        ),
                    ) { actions ->
                        Text("Row", actions.size(200.dp, 48.dp).clickable { events += "click" })
                    }
                }
            }
        }
    }

    @Test
    fun rightClickOpensTheMenuWithoutClickingTheRow() {
        show()
        rule.onNodeWithText("Row").performMouseInput { rightClick() }
        rule.onNodeWithText("Copy").performClick()
        rule.waitForIdle()
        assertEquals(listOf("copy"), events)
        assertTrue(rule.onAllNodesWithTextCount("Copy") == 0, "the menu closes after a choice")
    }

    @Test
    fun disabledEntriesCanNotBeChosen() {
        show()
        rule.onNodeWithText("Row").performMouseInput { rightClick() }
        rule.onNodeWithText("Delete").assertIsNotEnabled()
    }

    @Test
    fun aLeftClickStillClicksTheRow() {
        show()
        rule.onNodeWithText("Row").performClick()
        assertEquals(listOf("click"), events)
        assertEquals(0, rule.onAllNodesWithTextCount("Copy"))
    }

    @Test
    fun theMenuKeyOpensItForTheFocusedRow() {
        show()
        rule.onNodeWithText("Row").requestFocus()
        rule.onNodeWithText("Row").performKeyInput { pressKey(Key.Menu) }
        rule.onNodeWithText("Copy").performClick()
        assertEquals(listOf("copy"), events)
    }

    @Test
    fun escapeClosesTheMenuWithoutReachingThePage() {
        show()
        rule.onNodeWithText("Row").performMouseInput { rightClick() }
        // The menu is a popup: a root of its own, the last one.
        rule.onAllNodes(isRoot()).onLast().performKeyInput { pressKey(Key.Escape) }
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodesWithTextCount("Copy"))
        assertEquals(emptyList(), events, "the window's Esc goes back a page; the menu must keep it")
    }

    @Test
    fun entriesAreOfferedAsAccessibilityActions() {
        show()
        val actions = rule.onNodeWithText("Row").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Copy"), actions.map { it.label })
        rule.runOnIdle { actions.single().action() }
        assertEquals(listOf("copy"), events)
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String): Int =
    onAllNodes(
        androidx.compose.ui.test
            .hasText(text),
    ).fetchSemanticsNodes().size
