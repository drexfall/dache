package dev.dache.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import dev.dache.app.ui.layout.FormFactor
import dev.dache.app.ui.layout.LocalPullToRefresh
import dev.dache.app.ui.layout.PageScaffold
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** Pull to refresh follows the platform, not the width: tablets and landscape phones get it too. */
class PullToRefreshTest {
    @get:Rule
    val rule = createComposeRule()

    private fun pullDown(
        formFactor: FormFactor,
        pullToRefresh: Boolean,
    ): Int {
        var refreshes = 0
        rule.setContent {
            TestHost(formFactor) {
                CompositionLocalProvider(LocalPullToRefresh provides pullToRefresh) {
                    PageScaffold(title = "Page", isRefreshing = false, onRefresh = { refreshes++ }, onOpenMenu = null) {
                        Text("Body")
                    }
                }
            }
        }
        rule.onNodeWithText("Body").performTouchInput { swipeDown(startY = top, endY = top + 600f) }
        rule.waitForIdle()
        return refreshes
    }

    @Test
    fun expandedLayoutRefreshesOnPullWhenThePlatformIsTouchFirst() {
        assertEquals(1, pullDown(FormFactor.EXPANDED, pullToRefresh = true))
    }

    @Test
    fun compactLayoutRefreshesOnPullWhenThePlatformIsTouchFirst() {
        assertEquals(1, pullDown(FormFactor.COMPACT, pullToRefresh = true))
    }

    @Test
    fun desktopNeverRefreshesOnPull() {
        assertEquals(0, pullDown(FormFactor.COMPACT, pullToRefresh = false))
    }
}
