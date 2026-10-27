package dev.dache.core.dashboard

import dev.dache.core.model.DeployStatus
import dev.dache.core.model.ProviderId
import dev.dache.core.provider.mock.MockFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class HistoryTest {
    private val now = Instant.parse("2026-10-27T15:30:00Z")
    private val today = Instant.parse("2026-10-27T00:00:00Z")

    @Test
    fun startOfUtcDayTruncates() {
        assertEquals(today, startOfUtcDay(now))
        assertEquals(today, startOfUtcDay(today))
    }

    @Test
    fun dailyCountsCoverEveryDayOldestFirst() {
        val events =
            listOf(
                StatusEvent(now - 1.hours, DeployStatus.SUCCESS),
                StatusEvent(now - 2.hours, DeployStatus.FAILED),
                StatusEvent(now - 1.days, DeployStatus.CANCELLED),
                StatusEvent(now - 1.days, DeployStatus.SUCCESS),
                // Running builds and anything outside the window are left out.
                StatusEvent(now, DeployStatus.BUILDING),
                StatusEvent(now - 30.days, DeployStatus.FAILED),
            )

        val counts = dailyStatusCounts(events, days = 7, now = now)

        assertEquals(7, counts.size)
        assertEquals(today - 6.days, counts.first().day)
        assertEquals(DayCounts(today, succeeded = 1, failed = 1, cancelled = 0), counts.last())
        assertEquals(DayCounts(today - 1.days, succeeded = 1, failed = 0, cancelled = 1), counts[5])
        assertTrue(counts.take(5).all { it.total == 0 })
    }

    @Test
    fun latestPerWorkflowKeepsNewestRunOfEach() {
        val runs = MockFixtures.snapshot(ProviderId.GITHUB, "acc", now).ciRuns

        val latest = runs.latestPerWorkflow()

        assertEquals(5, latest.size)
        assertEquals(setOf("9001", "9002", "9003", "9004", "9005"), latest.map { it.nativeId }.toSet())
        assertTrue(runs.size > latest.size)
    }

    @Test
    fun eventsCarryWhatWasBuiltAndWhereToSeeIt() {
        val snapshot = MockFixtures.snapshot(ProviderId.NETLIFY, "acc", now)

        val events = snapshot.deployEvents()

        assertTrue(events.all { it.title in setOf("acme-marketing", "acme-docs") && it.webUrl != null })
    }

    @Test
    fun onUtcDayKeepsOnlyThatDayNewestFirst() {
        val events =
            listOf(
                StatusEvent(now - 3.hours, DeployStatus.SUCCESS, "a"),
                StatusEvent(now - 1.hours, DeployStatus.FAILED, "b"),
                StatusEvent(now - 1.days, DeployStatus.SUCCESS, "c"),
            )

        assertEquals(listOf("b", "a"), events.onUtcDay(today).map { it.title })
    }

    @Test
    fun medianUsesLowerMiddle() {
        assertNull(median(emptyList()))
        assertEquals(3, median(listOf(5, 1, 3)))
        assertEquals(2, median(listOf(4, 1, 2, 3)))
    }

    @Test
    fun mockHistoryIsDeterministic() {
        ProviderId.entries.forEach { id ->
            assertEquals(MockFixtures.snapshot(id, "acc", now), MockFixtures.snapshot(id, "acc", now))
        }
    }

    @Test
    fun mockHistoryStaysInsideTheChartWindow() {
        val snapshot = MockFixtures.snapshot(ProviderId.NETLIFY, "acc", now)

        val counts = dailyStatusCounts(snapshot.deployEvents(), days = 14, now = now)

        assertEquals(snapshot.sites.sumOf { it.deployments.size }, counts.sumOf { it.total })
    }
}
