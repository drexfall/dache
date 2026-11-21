package dev.dache.core.boards

import kotlinx.serialization.Serializable

/**
 * A board the user put together: a grid of [widgets] in reading order, each [Widget.span] columns wide and
 * [Widget.effectiveHeight] rows tall, placed by [BoardGrid]. It belongs to one profile and shows that profile's
 * accounts. [icon] is shown beside its name; null shows the default.
 */
@Serializable
data class Board(
    val id: String,
    val name: String,
    val profileId: String,
    val widgets: List<Widget> = emptyList(),
    val icon: BoardIcon? = null,
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

    /** These [widgets] in this order, each made valid; what the editor saves. */
    fun withWidgets(widgets: List<Widget>): Board = copy(widgets = widgets.map { it.normalized() })

    companion object {
        const val MAX_NAME_LENGTH = 40
    }
}

/**
 * One tile on a [Board]: [datapoint] over the last [days] days for the accounts in [scope], drawn as [chart].
 * A blank [title] shows the datapoint's own name. [aggregation] turns a series into one number for a stat; null
 * uses the datapoint's default. [height] is in grid rows; null uses the chart's default, so boards saved before
 * widgets had a height keep their look.
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
    val height: Int? = null,
) {
    val effectiveAggregation: Aggregation get() = aggregation ?: datapoint.defaultAggregation

    val effectiveHeight: Int get() = height ?: chart.defaultHeight

    /** This widget with every choice made valid for its datapoint, so a stored widget can always be drawn. */
    fun normalized(): Widget =
        copy(
            chart = chart.takeIf { it in datapoint.charts } ?: datapoint.charts.first(),
            title = title.trim().take(MAX_TITLE_LENGTH),
            aggregation = aggregation?.takeIf { datapoint.shape == DatapointShape.SERIES },
            days = days.takeIf { it in DAY_RANGES } ?: DEFAULT_DAYS,
            span = span.coerceIn(1, MAX_SPAN),
            height = height?.coerceIn(1, MAX_HEIGHT),
        )

    companion object {
        const val MAX_SPAN = 4
        const val MAX_HEIGHT = 6
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

/** Lists start tall enough to show a handful of items. */
private const val LIST_HEIGHT = 3

/** How a widget is drawn; [defaultHeight] is how many grid rows it takes until the user resizes it. */
enum class WidgetChart(
    val defaultHeight: Int,
) {
    /** One number, e.g. "42 deploys". */
    STAT(1),
    LINE(2),
    BARS(2),

    /** The things counted, each with its status. */
    LIST(LIST_HEIGHT),

    /** A row of day cells per monitor, days with downtime marked; only for [Datapoint.UPTIME]. */
    STRIP(2),
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
                DatapointShape.SERIES if this == UPTIME ->
                    listOf(WidgetChart.LINE, WidgetChart.STRIP, WidgetChart.BARS, WidgetChart.STAT)
                DatapointShape.SERIES ->
                    if (unit == ValueUnit.COUNT || unit == ValueUnit.MONEY) {
                        listOf(WidgetChart.BARS, WidgetChart.LINE, WidgetChart.STAT)
                    } else {
                        listOf(WidgetChart.LINE, WidgetChart.BARS, WidgetChart.STAT)
                    }
                DatapointShape.SET -> listOf(WidgetChart.STAT, WidgetChart.LIST)
            }
}
