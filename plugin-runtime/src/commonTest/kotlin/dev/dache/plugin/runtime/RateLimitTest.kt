package dev.dache.plugin.runtime

import dev.dache.core.account.tokenKey
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.plugin.api.LimitsSpec
import dev.dache.plugin.api.PluginManifest
import dev.dache.plugin.runtime.http.RequestBudgetException
import dev.dache.plugin.runtime.http.createPluginHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class RateLimitTest {
    private val account = ConnectedAccount(id = "acc-1", providerId = ProviderId.NETLIFY, displayName = "dev")
    private val vault = MapTokenVault().apply { tokens[account.tokenKey()] = "fake-token-123" }

    /** Virtual times, in ms, at which each request was sent. */
    private val sentAt = mutableListOf<Long>()

    private fun TestScope.provider(
        limits: LimitsSpec = LimitsSpec(),
        answer: MockRequestHandleScope.(index: Int, HttpRequestData) -> HttpResponseData = { _, _ -> page() },
    ): ManifestProvider {
        val manifest: PluginManifest = TestManifests.sites.copy(limits = limits)
        return ManifestProvider(
            manifest = manifest,
            vault = vault,
            client =
                createPluginHttpClient(
                    MockEngine { request ->
                        sentAt += currentTime
                        answer(sentAt.lastIndex, request)
                    },
                ),
            timeSource = testScheduler.timeSource,
        )
    }

    private fun MockRequestHandleScope.page(
        status: HttpStatusCode = HttpStatusCode.OK,
        vararg headers: Pair<String, String>,
    ): HttpResponseData =
        respond(
            if (status == HttpStatusCode.OK) "[{\"id\":\"1\",\"name\":\"a\"},{\"id\":\"2\",\"name\":\"b\"}]" else "{}",
            status,
            headersOf(
                *(
                    headers.map { it.first to listOf(it.second) } +
                        (HttpHeaders.ContentType to listOf("application/json"))
                ).toTypedArray(),
            ),
        )

    @Test
    fun requestsArePacedToTheManifestsLimit() =
        runTest {
            // Three full pages: the third waits for the first to leave the one-minute window.
            provider(LimitsSpec(requestsPerMinute = 2)).fetchSnapshot(account).getOrThrow()

            assertEquals(listOf(0L, 0L, 60_000L), sentAt)
        }

    @Test
    fun aShortRetryAfterIsWaitedOutAndRetried() =
        runTest {
            val provider =
                provider { index, _ ->
                    if (index == 0) page(HttpStatusCode.TooManyRequests, HttpHeaders.RetryAfter to "3") else page()
                }

            provider.fetchSnapshot(account).getOrThrow()

            assertEquals(listOf(0L, 3_000L), sentAt.take(2))
        }

    @Test
    fun aLongRetryAfterFailsAtOnce() =
        runTest {
            val provider = provider { _, _ -> page(HttpStatusCode.TooManyRequests, HttpHeaders.RetryAfter to "120") }

            val error = provider.fetchSnapshot(account).exceptionOrNull()

            assertIs<ProviderError.RateLimited>(error)
            assertEquals(120.seconds, error.retryAfter)
            assertEquals(1, sentAt.size)
        }

    @Test
    fun noRequestsLeftHoldsTheNextUntilTheReset() =
        runTest {
            val provider =
                provider { index, _ ->
                    if (index ==
                        0
                    ) {
                        page(HttpStatusCode.OK, "X-RateLimit-Remaining" to "0", "X-RateLimit-Reset" to "5")
                    } else {
                        page()
                    }
                }

            provider.fetchSnapshot(account).getOrThrow()

            assertEquals(5_000L, sentAt[1])
        }

    @Test
    fun aRefreshStopsAtItsRequestBudget() =
        runTest {
            val error = provider(LimitsSpec(maxRequestsPerRefresh = 2)).fetchSnapshot(account).exceptionOrNull()

            assertIs<ProviderError.Unknown>(error)
            // Debug builds of coroutines may wrap the error again while recovering stack traces.
            assertTrue(generateSequence(error.cause) { it.cause }.any { it is RequestBudgetException })
            assertEquals(2, sentAt.size)
        }
}
