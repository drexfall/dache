package dev.dache.core.dashboard

import dev.dache.core.model.ProviderId
import dev.dache.core.provider.mock.MockFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class DashboardSummaryTest {
    private val now = Instant.parse("2026-10-27T12:00:00Z")

    @Test
    fun countsProblemsAcrossProviders() {
        val snapshots = ProviderId.entries.map { MockFixtures.snapshot(it, "acc", now) }

        val summary = DashboardSummary.from(snapshots, now)

        assertEquals(
            DashboardSummary(failingDeploys = 1, downMonitors = 1, expiringDomains = 1, failedCiRuns = 1),
            summary,
        )
        assertTrue(summary.hasProblems)
    }

    @Test
    fun domainOutsideExpiryWindowIsNotCounted() {
        val snapshot = MockFixtures.snapshot(ProviderId.CLOUDFLARE, "acc", now)

        val summary = DashboardSummary.from(listOf(snapshot), now - 10.days)

        assertEquals(0, summary.expiringDomains)
    }

    @Test
    fun emptyDashboardHasNoProblems() {
        assertFalse(DashboardSummary.from(emptyList(), now).hasProblems)
    }
}
