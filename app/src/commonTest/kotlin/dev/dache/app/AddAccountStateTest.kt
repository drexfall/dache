package dev.dache.app

import dev.dache.app.accounts.AddAccountState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddAccountStateTest {
    @Test
    fun toStringLeavesTheTokenOut() {
        val text = AddAccountState(token = "fake-token-123").toString()

        assertFalse("fake-token-123" in text)
        assertTrue("token=***" in text)
    }
}
