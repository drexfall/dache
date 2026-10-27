package dev.dache.app.ui.components

import androidx.compose.runtime.Composable
import dev.dache.app.resources.Res
import dev.dache.app.resources.error_missing_scope
import dev.dache.app.resources.error_network
import dev.dache.app.resources.error_not_found
import dev.dache.app.resources.error_rate_limited
import dev.dache.app.resources.error_rate_limited_retry
import dev.dache.app.resources.error_server
import dev.dache.app.resources.error_unauthorized
import dev.dache.app.resources.error_unknown
import dev.dache.app.resources.error_vault_unavailable
import dev.dache.app.resources.time_days_ago
import dev.dache.app.resources.time_hours_ago
import dev.dache.app.resources.time_in_days
import dev.dache.app.resources.time_just_now
import dev.dache.app.resources.time_minutes_ago
import dev.dache.app.resources.value_minutes_seconds
import dev.dache.app.resources.value_ms
import dev.dache.app.resources.value_seconds
import dev.dache.app.ui.chart.compactNumber
import dev.dache.app.ui.chart.formatDecimal
import dev.dache.core.model.ProviderError
import dev.dache.core.security.VaultUnavailableException
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
fun relativeTime(
    instant: Instant,
    now: Instant = Clock.System.now(),
): String {
    val elapsed = now - instant
    return when {
        elapsed.isNegative() -> stringResource(Res.string.time_in_days, (-elapsed).inWholeDays.toInt())
        elapsed.inWholeMinutes < 1 -> stringResource(Res.string.time_just_now)
        elapsed.inWholeHours < 1 -> stringResource(Res.string.time_minutes_ago, elapsed.inWholeMinutes.toInt())
        elapsed.inWholeDays < 1 -> stringResource(Res.string.time_hours_ago, elapsed.inWholeHours.toInt())
        else -> stringResource(Res.string.time_days_ago, elapsed.inWholeDays.toInt())
    }
}

@Composable
fun errorMessage(error: Throwable): String =
    when (error) {
        is ProviderError.Unauthorized -> stringResource(Res.string.error_unauthorized)
        is ProviderError.MissingScope -> stringResource(Res.string.error_missing_scope)
        is ProviderError.RateLimited ->
            error.retryAfter
                ?.let { stringResource(Res.string.error_rate_limited_retry, it.inWholeSeconds.toInt()) }
                ?: stringResource(Res.string.error_rate_limited)
        is ProviderError.NotFound -> stringResource(Res.string.error_not_found)
        is ProviderError.Network -> stringResource(Res.string.error_network)
        is ProviderError.Server -> stringResource(Res.string.error_server, error.code)
        is VaultUnavailableException -> stringResource(Res.string.error_vault_unavailable)
        else -> stringResource(Res.string.error_unknown)
    }

/**
 * Plain (non-composable) value formatters for chart callbacks. Unit templates are resolved from string
 * resources once per composition so they stay localizable.
 */
class ValueFormats(
    private val msTemplate: String,
    private val secondsTemplate: String,
    private val minutesSecondsTemplate: String,
) {
    fun count(value: Double): String = compactNumber(value)

    fun milliseconds(value: Double): String = msTemplate.fill(compactNumber(kotlin.math.round(value)))

    fun duration(seconds: Double): String {
        val total = seconds.toInt()
        return if (total < SECONDS_PER_MINUTE) {
            secondsTemplate.fill(total.toString())
        } else {
            minutesSecondsTemplate.fill(
                (total / SECONDS_PER_MINUTE).toString(),
                (total % SECONDS_PER_MINUTE).toString(),
            )
        }
    }

    fun money(
        value: Double,
        currency: String,
    ): String {
        // Sign goes before the symbol: -$43.17, not $-43.17.
        val sign = if (value < 0) "-" else ""
        val amount = formatDecimal(abs(value), 2)
        return if (currency == "USD") "$sign$$amount" else "$sign$amount $currency"
    }

    fun percent(
        value: Double,
        decimals: Int = 2,
    ): String = formatDecimal(value, decimals) + "%"

    private fun String.fill(vararg args: String): String =
        args.foldIndexed(this) { index, text, arg -> text.replace("%${index + 1}\$s", arg) }

    private companion object {
        const val SECONDS_PER_MINUTE = 60
    }
}

@Composable
fun rememberValueFormats(): ValueFormats =
    ValueFormats(
        msTemplate = stringResource(Res.string.value_ms),
        secondsTemplate = stringResource(Res.string.value_seconds),
        minutesSecondsTemplate = stringResource(Res.string.value_minutes_seconds),
    )
