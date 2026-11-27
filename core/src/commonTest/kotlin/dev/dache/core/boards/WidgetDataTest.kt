package dev.dache.core.boards

import dev.dache.core.dashboard.AccountHistory
import dev.dache.core.dashboard.AccountResult
import dev.dache.core.dashboard.DashboardState
import dev.dache.core.dashboard.StatusEvent
import dev.dache.core.model.Billing
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.DeployStatus
import dev.dache.core.model.Deployment
import dev.dache.core.model.Domain
import dev.dache.core.model.Monitor
import dev.dache.core.model.MonitorStatus
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.Site
import dev.dache.core.model.TimedValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class WidgetDataTest {
    private val now = Instant.parse("2026-11-15T12:00:00Z")
    private val today = Instant.parse("2026-11-15T00:00:00Z")
    private val netlify = ConnectedAccount("n1", ProviderId.NETLIFY, "acme")
    private val vercel = ConnectedAccount("v1", ProviderId.VERCEL, "acme")
    private val robot = ConnectedAccount("u1", ProviderId.UPTIMEROBOT, "acme")

    private fun site(
        account: ConnectedAccount,
        name: String,
        vararg deploys: Pair<Instant, DeployStatus>,
    ) = Site(
        account.providerId,
        account.id,
        name,
        "https://example.com/$name",
        name,
        "https://$name.example.app",
        emptyList(),
        null,
        deploys.mapIndexed { i, (at, status) -> Deployment("$name-$i", "https://x", status, "main", null, at, 30) },
    )

    private fun monitor(
        name: String,
        status: MonitorStatus,
        responseTimes: List<TimedValue> = emptyList(),
        dailyUptime: List<TimedValue> = emptyList(),
    ) = Monitor(
        robot.providerId,
        robot.id,
        name,
        "https://x",
        name,
        "https://$name",
        status,
        99.5,
        120,
        responseTimes,
        dailyUptime,
    )

    private val state =
        DashboardState(
            accounts = listOf(netlify, vercel, robot),
            results =
                listOf(
                    AccountResult(
                        netlify,
                        ProviderSnapshot(
                            sites =
                                listOf(
                                    site(netlify, "blog", today + 1.hours to DeployStatus.SUCCESS),
                                    site(netlify, "shop", today - 1.days to DeployStatus.FAILED),
                                ),
                            billing =
                                Billing(
                                    "USD",
                                    3.0,
                                    0.0,
                                    listOf(TimedValue(today - 1.days, 1.0), TimedValue(today, 2.0)),
                                ),
                        ),
                        error = null,
                    ),
                    AccountResult(
                        vercel,
                        ProviderSnapshot(
                            sites = listOf(site(vercel, "docs", today + 2.hours to DeployStatus.SUCCESS)),
                            domains =
                                listOf(
                                    Domain(vercel.providerId, vercel.id, "d1", "https://x", "soon.dev", now + 5.days),
                                    Domain(
                                        vercel.providerId,
                                        vercel.id,
                                        "d2",
                                        "https://x",
                                        "later.dev",
                                        now + 200.days,
                                    ),
                                ),
                            billing = Billing("EUR", 1.0, 0.0, listOf(TimedValue(today, 5.0))),
                        ),
                        error = null,
                    ),
                    AccountResult(
                        robot,
                        ProviderSnapshot(
                            monitors =
                                listOf(
                                    monitor(
                                        "api",
                                        MonitorStatus.UP,
                                        listOf(
                                            TimedValue(today + 1.hours, 100.0),
                                            TimedValue(
                                                today + 2.hours,
                                                300.0,
                                            ),
                                        ),
                                        dailyUptime = listOf(TimedValue(today, 100.0)),
                                    ),
                                    monitor(
                                        "web",
                                        MonitorStatus.DOWN,
                                        dailyUptime =
                                            listOf(
                                                TimedValue(today - 20.days, 50.0),
                                                TimedValue(today - 1.days, 100.0),
                                                TimedValue(today, 90.0),
                                            ),
                                    ),
                                    monitor("idle", MonitorStatus.PAUSED),
                                ),
                        ),
                        error = null,
                    ),
                ),
            // Stored history reaches back past what Netlify lists now.
            histories =
                mapOf(
                    netlify.id to
                        AccountHistory(
                            netlify.id,
                            deploys = listOf(StatusEvent(today - 3.days, DeployStatus.FAILED, key = "old")),
                        ),
                ),
        )

    private fun value(widget: Widget) = WidgetData.evaluate(widget.normalized(), state, now)

    @Test
    fun deployCountsMergeHistoryAndCoverEveryDay() {
        val series = assertIs<WidgetValue.Series>(value(Widget("w", Datapoint.DEPLOYS, days = 7)))

        assertEquals(7, series.points.size)
        assertEquals(listOf(0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 2.0), series.points.map { it.value })
        assertEquals(today, series.points.last().at)
    }

    @Test
    fun scopeNarrowsToAProviderOrAnAccount() {
        fun total(
            datapoint: Datapoint,
            scope: WidgetScope,
        ) = assertIs<WidgetValue.Stat>(value(Widget("w", datapoint, WidgetChart.STAT, scope = scope))).value

        assertEquals(4.0, total(Datapoint.DEPLOYS, WidgetScope()))
        assertEquals(1.0, total(Datapoint.DEPLOYS, WidgetScope(providerId = "vercel")))
        assertEquals(3.0, total(Datapoint.DEPLOYS, WidgetScope(accountId = "n1")))
        assertEquals(2.0, total(Datapoint.FAILED_DEPLOYS, WidgetScope(accountId = "n1")))
    }

    @Test
    fun aStatAggregatesTheSeries() {
        fun stat(aggregation: Aggregation?) =
            assertIs<WidgetValue.Stat>(
                value(Widget("w", Datapoint.RESPONSE_TIME, WidgetChart.STAT, aggregation = aggregation)),
            )

        assertEquals(200.0, stat(null).value, "response time averages by default")
        assertEquals(200.0, stat(Aggregation.MAX).value, "one day of data, so its daily average")
        assertEquals(ValueUnit.MILLISECONDS, stat(null).unit)
        assertNull(
            assertIs<WidgetValue.Stat>(
                value(Widget("w", Datapoint.UPTIME, WidgetChart.STAT, scope = WidgetScope(providerId = "netlify"))),
            ).value,
            "no monitors, no average",
        )
    }

    @Test
    fun costKeepsItsCurrencyOnlyWhenThereIsOne() {
        val mixed = assertIs<WidgetValue.Series>(value(Widget("w", Datapoint.DAILY_COST)))
        assertEquals(listOf(1.0, 7.0), mixed.points.map { it.value })
        assertNull(mixed.currency)

        val euros =
            assertIs<WidgetValue.Series>(
                value(Widget("w", Datapoint.DAILY_COST, scope = WidgetScope(accountId = "v1"))),
            )
        assertEquals("EUR", euros.currency)
    }

    @Test
    fun setsListProblemsFirstOrCountThem() {
        val sites = assertIs<WidgetValue.Items>(value(Widget("w", Datapoint.SITES, WidgetChart.LIST)))
        assertEquals(listOf("shop", "blog", "docs"), sites.items.map { it.name })
        assertEquals(ItemTone.CRITICAL, sites.items.first().tone)

        val failing = assertIs<WidgetValue.Items>(value(Widget("w", Datapoint.FAILING_SITES, WidgetChart.LIST)))
        assertEquals(listOf("shop"), failing.items.map { it.name })

        val expiring = assertIs<WidgetValue.Stat>(value(Widget("w", Datapoint.EXPIRING_DOMAINS)))
        assertEquals(1.0, expiring.value)

        val down = assertIs<WidgetValue.Items>(value(Widget("w", Datapoint.MONITORS_DOWN, WidgetChart.LIST)))
        assertEquals(listOf("web"), down.items.map { it.name })
        assertEquals(
            robot.id,
            down.items
                .single()
                .ref.accountId,
        )
    }

    @Test
    fun uptimeStripsShowEachMonitorsDaysInRange() {
        val strips = assertIs<WidgetValue.Strips>(value(Widget("w", Datapoint.UPTIME, WidgetChart.STRIP, days = 7)))

        assertEquals(listOf("web", "api"), strips.rows.map { it.name }, "downtime first; no uptime, no row")
        val web = strips.rows.first()
        assertEquals(listOf(today - 1.days, today), web.days.map { it.at }, "the day 20 days ago is out of range")
        assertEquals(1, web.daysWithDowntime)
        assertEquals(95.0, web.average)
        assertEquals(robot.id, web.ref.accountId)

        val month = assertIs<WidgetValue.Strips>(value(Widget("w", Datapoint.UPTIME, WidgetChart.STRIP, days = 30)))
        assertEquals(
            3,
            month.rows
                .first()
                .days.size,
        )

        val elsewhere =
            value(Widget("w", Datapoint.UPTIME, WidgetChart.STRIP, scope = WidgetScope(providerId = "netlify")))
        assertEquals(emptyList(), assertIs<WidgetValue.Strips>(elsewhere).rows)
    }

    @Test
    fun onlyUptimeCanBeAStrip() {
        assertEquals(WidgetChart.STRIP, Widget("w", Datapoint.UPTIME, WidgetChart.STRIP).normalized().chart)
        assertEquals(WidgetChart.LINE, Widget("w", Datapoint.RESPONSE_TIME, WidgetChart.STRIP).normalized().chart)
        assertEquals(WidgetChart.STAT, Widget("w", Datapoint.MONITORS, WidgetChart.STRIP).normalized().chart)
    }

    @Test
    fun aStoredWidgetIsAlwaysDrawable() {
        val odd =
            Widget(
                "w",
                Datapoint.SITES,
                WidgetChart.LINE,
                title = "  x  ",
                aggregation = Aggregation.MAX,
                days = 3,
                span = 9,
            ).normalized()

        assertEquals(WidgetChart.STAT, odd.chart)
        assertEquals("x", odd.title)
        assertNull(odd.aggregation)
        assertEquals(Widget.DEFAULT_DAYS, odd.days)
        assertEquals(Widget.MAX_SPAN, odd.span)
    }
}
