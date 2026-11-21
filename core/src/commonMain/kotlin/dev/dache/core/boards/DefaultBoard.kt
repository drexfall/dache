package dev.dache.core.boards

/**
 * The board every profile starts with, laid out like the overview it replaced: the four problem counters, then
 * activity charts, then the resources behind them. [newId] names each widget.
 */
fun defaultBoard(
    id: String,
    name: String,
    profileId: String,
    newId: () -> String,
): Board {
    fun widget(
        datapoint: Datapoint,
        chart: WidgetChart = datapoint.charts.first(),
        span: Int = 1,
        height: Int? = null,
    ) = Widget(newId(), datapoint, chart, span = span, height = height)

    return Board(
        id = id,
        name = name,
        profileId = profileId,
        widgets =
            listOf(
                widget(Datapoint.FAILING_SITES),
                widget(Datapoint.MONITORS_DOWN),
                widget(Datapoint.EXPIRING_DOMAINS),
                widget(Datapoint.FAILED_CI_RUNS, WidgetChart.STAT),
                widget(Datapoint.DEPLOYS, span = 2),
                widget(Datapoint.CI_RUNS, span = 2),
                widget(Datapoint.RESPONSE_TIME, span = 2, height = 3),
                widget(Datapoint.UPTIME, WidgetChart.STRIP, span = 2, height = 3),
                widget(Datapoint.SITES, WidgetChart.LIST, span = 2),
                widget(Datapoint.MONITORS, WidgetChart.LIST, span = 2),
                widget(Datapoint.SERVERS, WidgetChart.LIST),
                widget(Datapoint.WORKERS, WidgetChart.LIST),
                widget(Datapoint.DOMAINS, WidgetChart.LIST, span = 2),
            ).map(Widget::normalized),
    )
}
