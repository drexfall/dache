package dev.dache.plugin.runtime.http

import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * The GET responses of one refresh, so a list that several resources fan out from (Cloudflare's accounts, a
 * provider's zones) is requested once rather than once per resource. Concurrent callers of the same request wait
 * for the first one. Failures are shared too: a request that was refused in this refresh is refused for every
 * resource that needs it. Carried in the coroutine context beside [RequestBudget], and only for one refresh, so
 * nothing outlives it.
 */
internal class RefreshCache : AbstractCoroutineContextElement(Key) {
    private val mutex = Mutex()
    private val responses = mutableMapOf<Pair<Url, Map<String, String>>, CompletableDeferred<Result<JsonResponse>>>()

    /** The response to GET [url] with [headers], sending it with [send] only if this refresh hasn't yet. */
    suspend fun get(
        url: Url,
        headers: Map<String, String>,
        send: suspend () -> JsonResponse,
    ): JsonResponse {
        val key = url to headers
        val (response, first) =
            mutex.withLock {
                responses[key]?.let { it to false }
                    ?: CompletableDeferred<Result<JsonResponse>>().also { responses[key] = it }.let { it to true }
            }
        if (first) {
            try {
                response.complete(Result.success(send()))
            } catch (e: CancellationException) {
                // A cancelled request decides nothing: let the next caller send it again.
                mutex.withLock { responses.remove(key) }
                response.completeExceptionally(e)
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                response.complete(Result.failure(e))
            }
        }
        return response.await().getOrThrow()
    }

    companion object Key : CoroutineContext.Key<RefreshCache>
}

/** [send] through the refresh's [RefreshCache], or directly outside a refresh. */
internal suspend fun cachedGet(
    url: Url,
    headers: Map<String, String>,
    send: suspend () -> JsonResponse,
): JsonResponse = coroutineContext[RefreshCache]?.get(url, headers, send) ?: send()
