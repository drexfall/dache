package dev.dache.core.boards

import dev.dache.core.dashboard.AccountHistory
import dev.dache.core.dashboard.AccountResult
import dev.dache.core.dashboard.DashboardState
import dev.dache.core.dashboard.StatusEvent
import dev.dache.core.dashboard.ciEvents
import dev.dache.core.dashboard.dailyStatusCounts
import dev.dache.core.dashboard.deployEvents
import dev.dache.core.dashboard.mergedWith
import dev.dache.core.dashboard.startOfUtcDay
import dev.dache.core.dashboard.withHistory
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.DeployStatus
import dev.dache.core.model.Monitor
import dev.dache.core.model.MonitorStatus
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.ResourceRef
import dev.dache.core.model.ServerStatus
import dev.dache.core.model.TimedValue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** How an item in a widget's list is doing, so the UI can color it without knowing what it is. */
enum class ItemTone { GOOD, WARNING, CRITICAL, NEUTRAL }

/** What a list shows beside an item's name; the UI words it. */
sealed interface ItemDetail {
    data class Deploy(
        val status: DeployStatus,
    ) : ItemDetail

    data class Expires(
        val at: Instant,
    ) : ItemDetail

    data class Server(
        val status: ServerStatus,
    ) : ItemDetail

    data class Monitor(
        val status: MonitorStatus,
        val uptimePercent: Double?,
    ) : ItemDetail
}

/** One resource in a [WidgetValue.Items]. */
data class WidgetItem(
    val name: String,
    val tone: ItemTone,
    val ref: ResourceRef,
    val detail: ItemDetail? = null,
)

/** What a widget shows, worked out from the dashboard; the UI only draws it. */
sealed interface WidgetValue {
    val unit: ValueUnit

    /** The currency of a [ValueUnit.MONEY] value; null when the accounts report none, or several. */
    val currency: String?

    /** A value per UTC day, oldest first. Count series have every day; measured ones only days with data. */
    data class Series(
        val points: List<TimedValue>,
        override val unit: ValueUnit,
        override val currency: String? = null,
    ) : WidgetValue

    /** One number; null when there is nothing to compute it from. */
    data class Stat(
        val value: Double?,
        override val unit: ValueUnit,
        override val currency: String? = null,
    ) : WidgetValue

    data class Items(
        val items: List<WidgetItem>,
    ) : WidgetValue {
        override val unit: ValueUnit get() = ValueUnit.COUNT
        override val currency: String? get() = null
    }
}

/** Works out widget values from the dashboard: live snapshots merged with stored history, as the built-in charts do. */
object WidgetData {
    fun evaluate(
        widget: Widget,
        state: DashboardState,
        now: Instant,
    ): WidgetValue {
        val results = state.results.filter { it.account.inScope(widget.scope) }
        val datapoint = widget.datapoint
        return when (datapoint.shape) {
            DatapointShape.SERIES -> {
                val series = series(datapoint, results, state, widget.days, now)
                if (widget.chart == WidgetChart.STAT) {
                    WidgetValue.Stat(
                        aggregate(series.points, widget.effectiveAggregation),
                        series.unit,
                        series.currency,
                    )
                } else {
                    series
                }
            }
            DatapointShape.SET -> {
                val items = widgetItems(datapoint, results.mapNotNull { it.snapshot }, now)
                if (widget.chart == WidgetChart.LIST) {
                    WidgetValue.Items(items)
                } else {
                    WidgetValue.Stat(items.size.toDouble(), ValueUnit.COUNT)
                }
            }
        }
    }

    fun aggregate(
        points: List<TimedValue>,
        aggregation: Aggregation,
    ): Double? {
        val values = points.map { it.value }
        return when (aggregation) {
            Aggregation.SUM -> values.sum()
            Aggregation.AVERAGE -> values.takeIf { it.isNotEmpty() }?.average()
            Aggregation.LATEST -> values.lastOrNull()
            Aggregation.MIN -> values.minOrNull()
            Aggregation.MAX -> values.maxOrNull()
        }
    }

    private fun ConnectedAccount.inScope(scope: WidgetScope): Boolean =
        (scope.accountId == null || scope.accountId == id) &&
            (scope.providerId == null || scope.providerId == providerId.id)

    private fun series(
        datapoint: Datapoint,
        results: List<AccountResult>,
        state: DashboardState,
        days: Int,
        now: Instant,
    ): WidgetValue.Series {
        val first = startOfUtcDay(now) - (days - 1).days
        return when (datapoint) {
            Datapoint.DEPLOYS, Datapoint.FAILED_DEPLOYS -> {
                val events = events(results, state) { history, snapshot -> history.deploys to snapshot.deployEvents() }
                counts(events, days, now, failedOnly = datapoint == Datapoint.FAILED_DEPLOYS)
            }
            Datapoint.CI_RUNS, Datapoint.FAILED_CI_RUNS -> {
                val events = events(results, state) { history, snapshot -> history.ciRuns to snapshot.ciEvents() }
                counts(events, days, now, failedOnly = datapoint == Datapoint.FAILED_CI_RUNS)
            }
            Datapoint.RESPONSE_TIME ->
                WidgetValue.Series(
                    dailyAverage(monitors(results, state).flatMap { it.responseTimes }, first),
                    datapoint.unit,
                )
            Datapoint.UPTIME ->
                WidgetValue.Series(
                    dailyAverage(monitors(results, state).flatMap { it.dailyUptime }, first),
                    datapoint.unit,
                )
            Datapoint.DAILY_COST -> {
                val billings = results.mapNotNull { it.snapshot?.billing }
                val points =
                    billings
                        .flatMap { it.dailyCost }
                        .filter { it.at >= first }
                        .groupBy { startOfUtcDay(it.at) }
                        .map { (day, values) -> TimedValue(day, values.sumOf { it.value }) }
                        .sortedBy { it.at }
                WidgetValue.Series(points, datapoint.unit, billings.map { it.currency }.distinct().singleOrNull())
            }
            else -> error("$datapoint is not a series")
        }
    }

    private fun events(
        results: List<AccountResult>,
        state: DashboardState,
        pick: (AccountHistory, ProviderSnapshot) -> Pair<List<StatusEvent>, List<StatusEvent>>,
    ): List<StatusEvent> =
        results.flatMap { result ->
            val history = state.history(result.account.id) ?: AccountHistory(result.account.id)
            val snapshot = result.snapshot ?: ProviderSnapshot()
            val (stored, live) = pick(history, snapshot)
            stored.mergedWith(live)
        }

    private fun counts(
        events: List<StatusEvent>,
        days: Int,
        now: Instant,
        failedOnly: Boolean,
    ): WidgetValue.Series =
        WidgetValue.Series(
            dailyStatusCounts(events, days, now).map {
                TimedValue(it.day, (if (failedOnly) it.failed else it.total).toDouble())
            },
            ValueUnit.COUNT,
        )

    private fun monitors(
        results: List<AccountResult>,
        state: DashboardState,
    ): List<Monitor> =
        results.flatMap { result ->
            val history = state.history(result.account.id)
            result.snapshot
                ?.monitors
                .orEmpty()
                .map { it.withHistory(history?.monitor(it.nativeId)) }
        }

    private fun dailyAverage(
        points: List<TimedValue>,
        first: Instant,
    ): List<TimedValue> =
        points
            .filter { it.at >= first }
            .groupBy { startOfUtcDay(it.at) }
            .map { (day, values) -> TimedValue(day, values.map { it.value }.average()) }
            .sortedBy { it.at }
}
