package dev.dache.plugin.runtime.http

import dev.dache.core.model.ProviderError
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.fromHttpToGmtDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.time.Clock
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Paces one token's requests. With [perMinute], at most that many go out in any minute. When the provider says
 * to wait, by `Retry-After` or by reporting no requests left until a reset, every request for this token waits;
 * a hold longer than [maxWait] fails at once with [ProviderError.RateLimited] instead of stalling the refresh.
 */
internal class RateLimiter(
    private val perMinute: Int?,
    private val timeSource: TimeSource.WithComparableMarks,
    private val maxWait: Duration = MAX_WAIT,
) {
    private val mutex = Mutex()
    private val sent = ArrayDeque<ComparableTimeMark>()
    private var blockedUntil: ComparableTimeMark? = null

    /** Suspends until a request may go out. The lock is held while waiting, so requests leave in order. */
    suspend fun acquire() {
        mutex.withLock {
            val now = timeSource.markNow()
            while (sent.isNotEmpty() && now - sent.first() >= 1.minutes) sent.removeFirst()
            val blocked = blockedUntil?.let { it - now } ?: Duration.ZERO
            val paced =
                if (perMinute != null && sent.size >= perMinute) sent.first() + 1.minutes - now else Duration.ZERO
            // Only the provider's own holds are capped: pacing to a known limit never waits more than a minute.
            if (blocked > maxWait) throw ProviderError.RateLimited(blocked)
            val wait = maxOf(blocked, paced, Duration.ZERO)
            if (wait > Duration.ZERO) delay(wait)
            sent.addLast(timeSource.markNow())
        }
    }

    /** Holds every request for this token for [duration]. */
    suspend fun block(duration: Duration) {
        mutex.withLock {
            val until = timeSource.markNow() + duration
            if (blockedUntil?.let { it < until } != false) blockedUntil = until
        }
    }

    companion object {
        /** Longer waits fail the request rather than keep a refresh, or a background check, waiting. */
        val MAX_WAIT = 15.seconds
    }
}

/**
 * How long a response says to wait: `Retry-After` (seconds or an HTTP date), or, once `X-RateLimit-Remaining` or
 * `RateLimit-Remaining` reaches 0, until the matching reset (epoch seconds as GitHub sends it, or seconds from now
 * as the IETF draft has it). Null when it doesn't say.
 */
internal fun waitFrom(
    headers: Headers,
    clock: Clock = Clock.System,
): Duration? {
    headers[HttpHeaders.RetryAfter]?.let { return parseDelay(it, clock) }
    val remaining = (headers["X-RateLimit-Remaining"] ?: headers["RateLimit-Remaining"])?.trim()?.toLongOrNull()
    val reset = (headers["X-RateLimit-Reset"] ?: headers["RateLimit-Reset"])?.trim()?.toLongOrNull()
    return if (remaining == 0L && reset != null) resetDelay(reset, clock) else null
}

private fun parseDelay(
    value: String,
    clock: Clock,
): Duration? =
    value.trim().toLongOrNull()?.seconds
        ?: runCatching { value.fromHttpToGmtDate().timestamp - clock.now().toEpochMilliseconds() }
            .getOrNull()
            ?.let { (it / MILLIS_PER_SECOND).seconds.coerceAtLeast(Duration.ZERO) }

private fun resetDelay(
    reset: Long,
    clock: Clock,
): Duration =
    if (reset > EPOCH_THRESHOLD) {
        (reset - clock.now().epochSeconds).seconds.coerceAtLeast(Duration.ZERO)
    } else {
        reset.seconds
    }

/** Reset values above this are epoch seconds (after 2001); below, seconds from now. */
private const val EPOCH_THRESHOLD = 1_000_000_000L
private const val MILLIS_PER_SECOND = 1000L

/**
 * The most requests one refresh may send for a plugin, shared by everything the refresh fans out to. Carried in
 * the coroutine context, so nested lists and fan-outs draw from the same budget.
 */
internal class RequestBudget(
    private val limit: Int,
) : AbstractCoroutineContextElement(Key) {
    private var used = 0
    private val mutex = Mutex()

    suspend fun take() {
        mutex.withLock {
            if (used >= limit) throw ProviderError.Unknown(RequestBudgetException(limit))
            used++
        }
    }

    companion object Key : CoroutineContext.Key<RequestBudget>
}

/** A refresh needed more requests than its plugin allows. */
class RequestBudgetException(
    limit: Int,
) : Exception("stopped after $limit requests in one refresh")

internal suspend fun takeFromBudget() {
    coroutineContext[RequestBudget]?.take()
}
