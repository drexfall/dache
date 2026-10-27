package dev.dache.core.provider.mock

import dev.dache.core.dashboard.startOfUtcDay
import dev.dache.core.model.DeployStatus
import dev.dache.core.model.TimedValue
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Deterministic fake time series. Each generator is seeded from a stable string so the same resource gets
 * the same history on every refresh, and tests can assert on it.
 */
internal object MockHistory {
    const val HISTORY_DAYS = 14
    const val UPTIME_DAYS = 30
    const val RESPONSE_HOURS = 24

    data class Build(
        val id: String,
        val at: Instant,
        val status: DeployStatus,
        val durationSeconds: Int,
    )

    /** First instant of the [HISTORY_DAYS] UTC-day window that history charts show. */
    fun windowStart(now: Instant): Instant = startOfUtcDay(now) - (HISTORY_DAYS - 1).days

    /** Past builds spread between [windowStart] and [before], newest first. */
    fun builds(
        seed: String,
        now: Instant,
        before: Instant,
        count: Int,
        failRate: Double,
        baseDurationSeconds: Int,
    ): List<Build> {
        val random = Random(seed.hashCode())
        val window = before - windowStart(now)
        if (!window.isPositive()) return emptyList()
        return (1..count)
            .map { index ->
                val roll = random.nextDouble()
                val status =
                    when {
                        roll < failRate -> DeployStatus.FAILED
                        roll < failRate + CANCEL_RATE -> DeployStatus.CANCELLED
                        else -> DeployStatus.SUCCESS
                    }
                Build(
                    id = "$seed-$index",
                    at = before - (random.nextDouble() * window.inWholeSeconds).seconds.coerceAtLeast(1.minutes),
                    status = status,
                    durationSeconds = (baseDurationSeconds * random.nextDouble(JITTER_LOW, JITTER_HIGH)).toInt(),
                )
            }.sortedByDescending { it.at }
    }

    /** Hourly response times ending at [now]. A monitor that is down stops reporting [downForHours] ago. */
    fun responseTimes(
        seed: String,
        now: Instant,
        baseMs: Int,
        downForHours: Int = 0,
    ): List<TimedValue> {
        val random = Random(seed.hashCode())
        val spikeHour = random.nextInt(RESPONSE_HOURS)
        // Checks land on the hour, like a monitor polling on a fixed schedule.
        val lastCheck = Instant.fromEpochSeconds(now.epochSeconds.floorDiv(SECONDS_PER_HOUR) * SECONDS_PER_HOUR)
        return (RESPONSE_HOURS - 1 downTo downForHours).map { hoursAgo ->
            val spike = if (RESPONSE_HOURS - 1 - hoursAgo == spikeHour) SPIKE_FACTOR else 1.0
            val ms = baseMs * random.nextDouble(JITTER_LOW, JITTER_HIGH) * spike
            TimedValue(at = lastCheck - hoursAgo.hours, value = ms.toInt().toDouble())
        }
    }

    /** Daily uptime percentages for the last [UPTIME_DAYS] UTC days, with roughly [incidentDays] bad days. */
    fun dailyUptime(
        seed: String,
        now: Instant,
        incidentDays: Int,
        todayPercent: Double? = null,
    ): List<TimedValue> {
        val random = Random(seed.hashCode())
        val badDays = (1 until UPTIME_DAYS).shuffled(random).take(incidentDays).toSet()
        val today = startOfUtcDay(now)
        return (UPTIME_DAYS - 1 downTo 0).map { daysAgo ->
            val percent =
                when {
                    daysAgo == 0 && todayPercent != null -> todayPercent
                    daysAgo in badDays -> random.nextDouble(BAD_DAY_MIN, BAD_DAY_MAX).roundTo(2)
                    else -> FULL_UPTIME
                }
            TimedValue(at = today - daysAgo.days, value = percent)
        }
    }

    /** Daily cost for the last [HISTORY_DAYS] UTC days. */
    fun dailyCost(
        seed: String,
        now: Instant,
        base: Double,
    ): List<TimedValue> {
        val random = Random(seed.hashCode())
        val today = startOfUtcDay(now)
        return (HISTORY_DAYS - 1 downTo 0).map { daysAgo ->
            TimedValue(
                at = today - daysAgo.days,
                value = (base * random.nextDouble(JITTER_LOW, JITTER_HIGH)).roundTo(2),
            )
        }
    }

    private fun Double.roundTo(decimals: Int): Double {
        var factor = 1.0
        repeat(decimals) { factor *= 10 }
        return kotlin.math.round(this * factor) / factor
    }

    private const val SECONDS_PER_HOUR = 3_600L
    private const val CANCEL_RATE = 0.06
    private const val JITTER_LOW = 0.75
    private const val JITTER_HIGH = 1.3
    private const val SPIKE_FACTOR = 3.2
    private const val FULL_UPTIME = 100.0
    private const val BAD_DAY_MIN = 97.5
    private const val BAD_DAY_MAX = 99.95
}
