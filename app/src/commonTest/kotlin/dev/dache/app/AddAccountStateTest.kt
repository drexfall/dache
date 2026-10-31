package dev.dache.app

import dev.dache.app.accounts.AddAccountState
import dev.dache.core.model.ProviderId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddAccountStateTest {
    @Test
    fun toStringLeavesTheTokenOut() {
        val text = AddAccountState(ProviderId.NETLIFY, token = "fake-token-123").toString()

        assertFalse("fake-token-123" in text)
        assertTrue("token=***" in text)
    }
}
