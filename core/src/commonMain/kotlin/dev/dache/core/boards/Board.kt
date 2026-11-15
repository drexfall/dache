package dev.dache.core.boards

import kotlinx.serialization.Serializable

/**
 * A dashboard the user put together: a grid of [widgets] in reading order, each [Widget.span] columns wide. It
 * belongs to one profile and shows that profile's accounts.
 */
@Serializable
data class Board(
    val id: String,
    val name: String,
    val profileId: String,
    val widgets: List<Widget> = emptyList(),
) {
    fun widget(id: String): Widget? = widgets.firstOrNull { it.id == id }

    /** [widget] in place of the one with its id, or added at the end. */
    fun withWidget(widget: Widget): Board {
        val normalized = widget.normalized()
        val index = widgets.indexOfFirst { it.id == widget.id }
        return copy(
            widgets =
                if (index < 0) {
                    widgets + normalized
                } else {
                    widgets.toMutableList().apply { set(index, normalized) }
                },
        )
    }

    fun withoutWidget(id: String): Board = copy(widgets = widgets.filterNot { it.id == id })

    /** Moves widget [id] [by] places in reading order (negative is earlier); stops at either end. */
    fun moved(
        id: String,
        by: Int,
    ): Board {
        val from = widgets.indexOfFirst { it.id == id }
        if (from < 0) return this
        val to = (from + by).coerceIn(0, widgets.lastIndex)
        return copy(widgets = widgets.toMutableList().apply { add(to, removeAt(from)) })
    }

    /** Moves widget [id] to [index] in reading order, e.g. where it was dropped. */
    fun movedTo(
        id: String,
        index: Int,
    ): Board {
        val from = widgets.indexOfFirst { it.id == id }
        return if (from < 0) this else moved(id, index.coerceIn(0, widgets.lastIndex) - from)
    }

    companion object {
        const val MAX_NAME_LENGTH = 40
    }
}

/**
 * One tile on a [Board]: [datapoint] over the last [days] days for the accounts in [scope], drawn as [chart].
 * A blank [title] shows the datapoint's own name. [aggregation] turns a series into one number for a stat; null
 * uses the datapoint's default.
 */
@Serializable
data class Widget(
    val id: String,
    val datapoint: Datapoint,
    val chart: WidgetChart = datapoint.charts.first(),
    val title: String = "",
    val scope: WidgetScope = WidgetScope(),
    val aggregation: Aggregation? = null,
    val days: Int = DEFAULT_DAYS,
    val span: Int = 1,
) {
    val effectiveAggregation: Aggregation get() = aggregation ?: datapoint.defaultAggregation

    /** This widget with every choice made valid for its datapoint, so a stored widget can always be drawn. */
    fun normalized(): Widget =
        copy(
            chart = chart.takeIf { it in datapoint.charts } ?: datapoint.charts.first(),
            title = title.trim().take(MAX_TITLE_LENGTH),
            aggregation = aggregation?.takeIf { datapoint.shape == DatapointShape.SERIES },
            days = days.takeIf { it in DAY_RANGES } ?: DEFAULT_DAYS,
            span = span.coerceIn(1, MAX_SPAN),
        )

    companion object {
        const val MAX_SPAN = 4
        const val DEFAULT_DAYS = 14
        const val MAX_TITLE_LENGTH = 60
        val DAY_RANGES = listOf(7, 14, 30, 90)
    }
}

/** Which accounts a widget reads: all of the profile's, one provider's, or one account's. */
@Serializable
data class WidgetScope(
    val providerId: String? = null,
    val accountId: String? = null,
)

enum class WidgetChart {
    /** One number, e.g. "42 deploys". */
    STAT,
    LINE,
    BARS,

    /** The things counted, each with its status. */
    LIST,
}

/** How a series becomes one number for a [WidgetChart.STAT]. */
enum class Aggregation { SUM, AVERAGE, LATEST, MIN, MAX }

enum class ValueUnit { COUNT, MILLISECONDS, PERCENT, MONEY }

/** A [DatapointShape.SERIES] is a value per day; a [DatapointShape.SET] is a set of resources, counted or listed. */
enum class DatapointShape { SERIES, SET }

/** What a widget can show: everything the app already collects, as a daily series or a set of resources. */
enum class Datapoint(
    val shape: DatapointShape,
    val unit: ValueUnit,
    val defaultAggregation: Aggregation = Aggregation.SUM,
) {
    DEPLOYS(DatapointShape.SERIES, ValueUnit.COUNT),
    FAILED_DEPLOYS(DatapointShape.SERIES, ValueUnit.COUNT),
    CI_RUNS(DatapointShape.SERIES, ValueUnit.COUNT),
    FAILED_CI_RUNS(DatapointShape.SERIES, ValueUnit.COUNT),
    RESPONSE_TIME(DatapointShape.SERIES, ValueUnit.MILLISECONDS, Aggregation.AVERAGE),
    UPTIME(DatapointShape.SERIES, ValueUnit.PERCENT, Aggregation.AVERAGE),
    DAILY_COST(DatapointShape.SERIES, ValueUnit.MONEY),
    SITES(DatapointShape.SET, ValueUnit.COUNT),
    FAILING_SITES(DatapointShape.SET, ValueUnit.COUNT),
    DOMAINS(DatapointShape.SET, ValueUnit.COUNT),
    EXPIRING_DOMAINS(DatapointShape.SET, ValueUnit.COUNT),
    SERVERS(DatapointShape.SET, ValueUnit.COUNT),
    WORKERS(DatapointShape.SET, ValueUnit.COUNT),
    MONITORS(DatapointShape.SET, ValueUnit.COUNT),
    MONITORS_DOWN(DatapointShape.SET, ValueUnit.COUNT),
    ;

    /** The charts this datapoint can be drawn as, the default first. */
    val charts: List<WidgetChart>
        get() =
            when (shape) {
                DatapointShape.SERIES ->
                    if (unit == ValueUnit.COUNT || unit == ValueUnit.MONEY) {
                        listOf(WidgetChart.BARS, WidgetChart.LINE, WidgetChart.STAT)
                    } else {
                        listOf(WidgetChart.LINE, WidgetChart.BARS, WidgetChart.STAT)
                    }
                DatapointShape.SET -> listOf(WidgetChart.STAT, WidgetChart.LIST)
            }
}
