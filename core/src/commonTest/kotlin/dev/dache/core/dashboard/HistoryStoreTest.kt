package dev.dache.core.dashboard

import dev.dache.core.model.DeployStatus
import dev.dache.core.model.Deployment
import dev.dache.core.model.Monitor
import dev.dache.core.model.MonitorStatus
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.ResourceKind
import dev.dache.core.model.ServicePage
import dev.dache.core.model.Site
import dev.dache.core.model.TimedValue
import dev.dache.core.provider.mock.MockFixtures
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class HistoryStoreTest {
    private val now = Instant.parse("2026-11-14T12:00:00Z")
    private val history = AccountHistory("acc")

    private fun site(vararg deploys: Deployment) =
        Site(
            ProviderId.NETLIFY,
            "acc",
            "site-1",
            "https://x",
            "docs",
            "https://docs",
            emptyList(),
            null,
            deploys.toList(),
        )

    private fun deploy(
        id: String,
        status: DeployStatus,
        at: Instant,
    ) = Deployment(id, "https://x/$id", status, "main", null, at, null)

    private fun monitor(
        status: MonitorStatus,
        responseTimes: List<TimedValue> = emptyList(),
        dailyUptime: List<TimedValue> = emptyList(),
    ) = Monitor(
        ProviderId.UPTIMEROBOT,
        "acc",
        "m1",
        "https://x",
        "api",
        "https://api",
        status,
        null,
        120,
        responseTimes,
        dailyUptime,
    )

    private fun sites(vararg deploys: Deployment) = ProviderSnapshot(sites = listOf(site(*deploys)))

    private fun monitors(monitor: Monitor) = ProviderSnapshot(monitors = listOf(monitor))

    @Test
    fun eventsAccumulateAcrossRefreshesAndAFinishedBuildReplacesItsRunningSelf() {
        val first = history.absorb(sites(deploy("d1", DeployStatus.BUILDING, now)), now)
        // Netlify lists one deploy per site: the next refresh shows d2 and d1, which has finished meanwhile.
        val later = now + 2.hours
        val second =
            first.absorb(
                sites(deploy("d2", DeployStatus.SUCCESS, later), deploy("d1", DeployStatus.FAILED, now)),
                later,
            )

        assertEquals(listOf("site-1/d1", "site-1/d2"), second.deploys.map { it.key })
        assertEquals(DeployStatus.FAILED, second.deploys.first().status)
        val third = second.absorb(sites(deploy("d2", DeployStatus.SUCCESS, later)), later)
        assertEquals(second.deploys, third.deploys)
    }

    @Test
    fun retentionDropsWhatIsOlderThanNinetyDays() {
        val old = history.absorb(sites(deploy("old", DeployStatus.SUCCESS, now - 100.days)), now - 95.days)
        assertEquals(1, old.deploys.size)

        val current = old.absorb(sites(deploy("new", DeployStatus.SUCCESS, now)), now)

        assertEquals(listOf("site-1/new"), current.deploys.map { it.key })
    }

    @Test
    fun monitorPointsMergeByBucketAndSamplesFillUnreportedDays() {
        val yesterday = startOfUtcDay(now) - 1.days
        val reported = listOf(TimedValue(yesterday, 99.5))
        var h = history.absorb(monitors(monitor(MonitorStatus.UP, dailyUptime = reported)), now)
        // Three refreshes today, one of them down: the provider reports no uptime for today yet.
        h = h.absorb(monitors(monitor(MonitorStatus.DOWN, dailyUptime = reported)), now + 20.minutes)
        h = h.absorb(monitors(monitor(MonitorStatus.UP, dailyUptime = reported)), now + 40.minutes)
        // Within one bucket of the previous sample, so it replaces it rather than adding a fourth.
        h = h.absorb(monitors(monitor(MonitorStatus.UP, dailyUptime = reported)), now + 44.minutes)

        val stored = h.monitor("m1")!!
        assertEquals(3, stored.samples.size)
        val shown = monitor(MonitorStatus.UP, dailyUptime = reported).withHistory(stored)
        assertEquals(listOf(yesterday, startOfUtcDay(now)), shown.dailyUptime.map { it.at })
        assertEquals(99.5, shown.dailyUptime.first().value)
        assertEquals(100.0 * 2 / 3, shown.dailyUptime.last().value)
    }

    @Test
    fun liveSeriesWinOverStoredPointsInTheSameBucket() {
        val at = now - 3.hours
        val stored =
            history.absorb(
                monitors(monitor(MonitorStatus.UP, responseTimes = listOf(TimedValue(at, 300.0)))),
                now,
            )
        val live =
            monitor(
                MonitorStatus.UP,
                responseTimes = listOf(TimedValue(at + 10.minutes, 120.0), TimedValue(now, 110.0)),
            )

        val shown = live.withHistory(stored.monitor("m1"))

        assertEquals(listOf(120.0, 110.0), shown.responseTimes.map { it.value })
    }

    @Test
    fun onlyKeepsThePagesKinds() {
        val full = history.absorb(MockFixtures.snapshot(ProviderId.GITHUB, "acc", now), now)
        assertTrue(full.ciRuns.isNotEmpty())

        val sites = full.only(ServicePage("sites", null, kinds = setOf(ResourceKind.SITE)))

        assertTrue(sites.ciRuns.isEmpty())
        assertEquals(full.deploys, sites.deploys)
    }

    @Test
    fun fileStoreRoundTripsAndForgetsRemovedAccounts() =
        runTest {
            val fileSystem = FakeFileSystem()
            val dir = "/data/history".toPath()
            val store = FileHistoryStore(fileSystem, dir)
            val a = AccountHistory("a").absorb(MockFixtures.snapshot(ProviderId.NETLIFY, "a", now), now)
            val b = AccountHistory("b").absorb(MockFixtures.snapshot(ProviderId.UPTIMEROBOT, "b", now), now)
            store.save(a)
            store.save(b)

            assertEquals(mapOf("a" to a, "b" to b), FileHistoryStore(fileSystem, dir).load())

            store.keepOnly(listOf("b"))
            assertEquals(setOf("b"), store.load().keys)
        }
}
