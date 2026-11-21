package dev.dache.app

import dev.dache.app.boards.firstFreeName
import kotlin.test.Test
import kotlin.test.assertEquals

class BoardNamesTest {
    private fun next(vararg taken: String) = firstFreeName(taken.toList()) { "Board $it" }

    @Test
    fun newBoardsCountFromOne() {
        assertEquals("Board 1", next("Overview"))
        assertEquals("Board 2", next("Overview", "Board 1"))
    }

    @Test
    fun aFreedNumberIsUsedAgainRatherThanRepeatingAName() {
        assertEquals("Board 2", next("Overview", "Board 1", "Board 3"))
        assertEquals("Board 1", next("Board 2", "Releases"), "renamed boards free their number")
    }
}
