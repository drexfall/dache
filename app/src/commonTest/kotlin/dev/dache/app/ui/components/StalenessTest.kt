package dev.dache.app.ui.components

import dev.dache.core.alerts.AlertSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class StalenessTest {
    private val now = Instant.parse("2026-11-14T12:00:00Z")

    @Test
    fun thresholdFollowsTheBackgroundCheckInterval() {
        assertEquals(1.hours, staleAfter(AlertSettings(enabled = false, intervalMinutes = 15)))
        assertEquals(30.minutes, staleAfter(AlertSettings(enabled = true, intervalMinutes = 30)))
    }

    @Test
    fun dataIsStaleOncePastTheThreshold() {
        assertFalse(isStale(now - 59.minutes, 1.hours, now))
        assertTrue(isStale(now - 61.minutes, 1.hours, now))
        assertFalse(isStale(now, 15.minutes, now))
    }
}
