package dev.dache.app.ui.chart

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/** A zero-based value axis with clean tick values (steps of 1, 2 or 5 × 10ⁿ). */
data class AxisScale(
    val max: Double,
    val ticks: List<Double>,
)

fun niceScale(
    maxValue: Double,
    targetTicks: Int = 4,
): AxisScale {
    if (maxValue <= 0.0 || maxValue.isNaN()) return AxisScale(max = 1.0, ticks = listOf(0.0, 1.0))
    val rawStep = maxValue / targetTicks
    val magnitude = 10.0.pow(floor(log10(rawStep)))
    val step = NICE_STEPS.map { it * magnitude }.first { it >= rawStep }
    val top = ceil(maxValue / step) * step
    val count = (top / step).roundToLong().toInt()
    return AxisScale(max = top, ticks = (0..count).map { it * step })
}

private val NICE_STEPS = listOf(1.0, 2.0, 5.0, 10.0)

/** Fixed-point formatting without platform formatters: `formatDecimal(1.5, 2)` → `1.50`. */
fun formatDecimal(
    value: Double,
    decimals: Int,
): String {
    val factor = 10.0.pow(decimals)
    val scaled = (abs(value) * factor).roundToLong()
    val whole = scaled / factor.toLong()
    val fraction = scaled % factor.toLong()
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    return if (decimals == 0) {
        "$sign$whole"
    } else {
        "$sign$whole.${fraction.toString().padStart(decimals, '0')}"
    }
}

/** Axis-tick style numbers: 0, 5, 250, 1.2K, 3M. Drops a trailing `.0`. */
fun compactNumber(value: Double): String {
    val absolute = abs(value)
    return when {
        absolute >= MILLION -> trimZero(formatDecimal(value / MILLION, 1)) + "M"
        absolute >= THOUSAND -> trimZero(formatDecimal(value / THOUSAND, 1)) + "K"
        absolute == floor(absolute) -> formatDecimal(value, 0)
        else -> trimZero(formatDecimal(value, 1))
    }
}

private fun trimZero(text: String) = text.removeSuffix(".0")

private const val THOUSAND = 1_000.0
private const val MILLION = 1_000_000.0
