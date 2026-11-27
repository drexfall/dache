package dev.dache.app.boards

import androidx.compose.runtime.Composable
import dev.dache.app.resources.Res
import dev.dache.app.resources.aggregation_average
import dev.dache.app.resources.aggregation_latest
import dev.dache.app.resources.aggregation_max
import dev.dache.app.resources.aggregation_min
import dev.dache.app.resources.aggregation_sum
import dev.dache.app.resources.datapoint_ci_runs
import dev.dache.app.resources.datapoint_daily_cost
import dev.dache.app.resources.datapoint_deploys
import dev.dache.app.resources.datapoint_domains
import dev.dache.app.resources.datapoint_expiring_domains
import dev.dache.app.resources.datapoint_failed_ci_runs
import dev.dache.app.resources.datapoint_failed_deploys
import dev.dache.app.resources.datapoint_failing_sites
import dev.dache.app.resources.datapoint_monitors
import dev.dache.app.resources.datapoint_monitors_down
import dev.dache.app.resources.datapoint_response_time
import dev.dache.app.resources.datapoint_servers
import dev.dache.app.resources.datapoint_sites
import dev.dache.app.resources.datapoint_uptime
import dev.dache.app.resources.datapoint_workers
import dev.dache.app.resources.widget_chart_bars
import dev.dache.app.resources.widget_chart_line
import dev.dache.app.resources.widget_chart_list
import dev.dache.app.resources.widget_chart_stat
import dev.dache.app.resources.widget_chart_strip
import dev.dache.app.ui.components.ValueFormats
import dev.dache.core.boards.Aggregation
import dev.dache.core.boards.Datapoint
import dev.dache.core.boards.ValueUnit
import dev.dache.core.boards.Widget
import dev.dache.core.boards.WidgetChart
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

val Datapoint.label: StringResource
    get() =
        when (this) {
            Datapoint.DEPLOYS -> Res.string.datapoint_deploys
            Datapoint.FAILED_DEPLOYS -> Res.string.datapoint_failed_deploys
            Datapoint.CI_RUNS -> Res.string.datapoint_ci_runs
            Datapoint.FAILED_CI_RUNS -> Res.string.datapoint_failed_ci_runs
            Datapoint.RESPONSE_TIME -> Res.string.datapoint_response_time
            Datapoint.UPTIME -> Res.string.datapoint_uptime
            Datapoint.DAILY_COST -> Res.string.datapoint_daily_cost
            Datapoint.SITES -> Res.string.datapoint_sites
            Datapoint.FAILING_SITES -> Res.string.datapoint_failing_sites
            Datapoint.DOMAINS -> Res.string.datapoint_domains
            Datapoint.EXPIRING_DOMAINS -> Res.string.datapoint_expiring_domains
            Datapoint.SERVERS -> Res.string.datapoint_servers
            Datapoint.WORKERS -> Res.string.datapoint_workers
            Datapoint.MONITORS -> Res.string.datapoint_monitors
            Datapoint.MONITORS_DOWN -> Res.string.datapoint_monitors_down
        }

val WidgetChart.label: StringResource
    get() =
        when (this) {
            WidgetChart.STAT -> Res.string.widget_chart_stat
            WidgetChart.LINE -> Res.string.widget_chart_line
            WidgetChart.BARS -> Res.string.widget_chart_bars
            WidgetChart.LIST -> Res.string.widget_chart_list
            WidgetChart.STRIP -> Res.string.widget_chart_strip
        }

val Aggregation.label: StringResource
    get() =
        when (this) {
            Aggregation.SUM -> Res.string.aggregation_sum
            Aggregation.AVERAGE -> Res.string.aggregation_average
            Aggregation.LATEST -> Res.string.aggregation_latest
            Aggregation.MIN -> Res.string.aggregation_min
            Aggregation.MAX -> Res.string.aggregation_max
        }

/** The widget's own title, or its datapoint's name when it has none. */
@Composable
fun Widget.displayTitle(): String = title.ifBlank { stringResource(datapoint.label) }

/** [value] in [unit]: "42", "180 ms", "99.95%", "$12.40". Money without one currency shows as a plain number. */
fun ValueFormats.format(
    value: Double,
    unit: ValueUnit,
    currency: String?,
): String =
    when (unit) {
        ValueUnit.COUNT -> count(value)
        ValueUnit.MILLISECONDS -> milliseconds(value)
        ValueUnit.PERCENT -> percent(value)
        ValueUnit.MONEY -> currency?.let { money(value, it) } ?: count(value)
    }
