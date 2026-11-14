package dev.dache.plugin.runtime.http

import dev.dache.core.model.ProviderError
import io.ktor.http.Headers
import io.ktor.http.Url
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class RefreshCacheTest {
    private val teams = Url("https://api.example.com/teams")
    private val response = JsonResponse(JsonArray(emptyList()), Headers.Empty)

    @Test
    fun concurrentCallersShareOneRequest() =
        runTest {
            val cache = RefreshCache()
            val release = CompletableDeferred<Unit>()
            var sent = 0

            val results =
                List(4) {
                    async {
                        cache.get(teams, emptyMap()) {
                            sent++
                            release.await()
                            response
                        }
                    }
                }
            launch { release.complete(Unit) }

            results.awaitAll().forEach { assertSame(response, it) }
            assertEquals(1, sent)
        }

    @Test
    fun differentHeadersAreDifferentRequests() =
        runTest {
            val cache = RefreshCache()
            var sent = 0

            cache.get(teams, mapOf("Accept" to "application/json")) { response.also { sent++ } }
            cache.get(teams, mapOf("Accept" to "application/vnd.github+json")) { response.also { sent++ } }

            assertEquals(2, sent)
        }

    @Test
    fun aFailureIsSharedWithinTheRefresh() =
        runTest {
            val cache = RefreshCache()
            var sent = 0

            repeat(2) {
                assertFailsWith<ProviderError.MissingScope> {
                    cache.get(teams, emptyMap()) {
                        sent++
                        throw ProviderError.MissingScope(scope = null)
                    }
                }
            }
            assertEquals(1, sent)
        }

    @Test
    fun aCancelledRequestIsSentAgain() =
        runTest {
            val cache = RefreshCache()
            val started = CompletableDeferred<Unit>()

            val first =
                launch {
                    cache.get(teams, emptyMap()) {
                        started.complete(Unit)
                        CompletableDeferred<JsonResponse>().await()
                    }
                }
            started.await()
            first.cancel()
            first.join()

            assertSame(response, cache.get(teams, emptyMap()) { response })
        }

    @Test
    fun outsideARefreshNothingIsShared() =
        runTest {
            var sent = 0

            repeat(2) { cachedGet(teams, emptyMap()) { response.also { sent++ } } }
            withContext(RefreshCache()) { repeat(2) { cachedGet(teams, emptyMap()) { response.also { sent++ } } } }

            assertEquals(3, sent)
        }
}
