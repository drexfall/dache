package dev.dache.plugin.runtime

import dev.dache.core.model.IconFormat
import dev.dache.core.model.ProviderId
import dev.dache.plugin.api.PluginManifest
import dev.dache.plugin.api.SelfHostedSpec
import dev.dache.plugin.runtime.http.createPluginHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Favicons: what is fetched, from where, and what is refused. Fake sites, fake images. */
class FaviconsTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val recording = Mutex()

    private fun png(size: Int = 8) =
        byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()) + ByteArray(size)

    private val ico = byteArrayOf(0, 0, 1, 0) + ByteArray(8)

    private fun html(head: String) = "<!doctype html><html><head>$head</head><body></body></html>"

    private fun MockRequestHandleScope.page(text: String): HttpResponseData =
        respond(text, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"))

    private fun MockRequestHandleScope.image(
        bytes: ByteArray,
        type: String = "image/png",
    ): HttpResponseData = respond(bytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, type))

    private fun provider(
        manifest: PluginManifest = TestManifests.sites,
        site: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = ManifestProvider(
        manifest = manifest,
        id = ProviderId.NETLIFY,
        vault = MapTokenVault(),
        client =
            createPluginHttpClient(
                MockEngine { request ->
                    recording.withLock { requests += request }
                    site(request)
                },
            ),
    )

    private val paths get() = requests.map { "${it.url.host}${it.url.encodedPath.ifEmpty { "/" }}" }

    @Test
    fun theSiteIsTheDashboardsOrigin() {
        assertEquals(
            "https://dashboard.example.com",
            provider { respondError(HttpStatusCode.NotFound) }.iconSite(emptyMap()),
        )
    }

    @Test
    fun picksTheBestLinkedIconFromTheHead() =
        runTest {
            val big = png(32)
            val provider =
                provider { request ->
                    when (request.url.encodedPath) {
                        "", "/" ->
                            page(
                                html(
                                    """
                                    <link rel="icon" href="/favicon-16.png" sizes="16x16">
                                    <link rel="icon" type="image/svg+xml" href="/icon.svg">
                                    <link REL='apple-touch-icon' href='https://cdn.example.net/touch.png?v=2&amp;x=1' sizes='180x180'>
                                    """.trimIndent(),
                                ),
                            )
                        "/touch.png" -> image(big)
                        else -> respondError(HttpStatusCode.NotFound)
                    }
                }

            val icon = provider.fetchIcon(emptyMap()).getOrThrow()

            assertEquals(IconFormat.PNG, icon?.format)
            assertTrue(icon!!.bytes.contentEquals(big))
            assertEquals(listOf("dashboard.example.com/", "cdn.example.net/touch.png"), paths)
            assertEquals("v=2&x=1", requests.last().url.encodedQuery)
        }

    @Test
    fun fallsBackToFaviconIcoAndSkipsLinksThatAreNotImages() =
        runTest {
            val provider =
                provider { request ->
                    when (request.url.encodedPath) {
                        "", "/" -> page(html("""<link rel="shortcut icon" href="/broken.png">"""))
                        "/broken.png" -> image("<html>not found</html>".encodeToByteArray(), "image/png")
                        "/favicon.ico" -> image(ico, "application/octet-stream")
                        else -> respondError(HttpStatusCode.NotFound)
                    }
                }

            val icon = provider.fetchIcon(emptyMap()).getOrThrow()

            assertEquals(IconFormat.ICO, icon?.format)
            assertEquals(
                listOf("/", "/broken.png", "/favicon.ico"),
                requests.map { it.url.encodedPath.ifEmpty { "/" } },
            )
        }

    @Test
    fun findsUnquotedLinksPastTheHead() =
        runTest {
            val touch = png(16)
            val provider =
                provider { request ->
                    when (request.url.encodedPath) {
                        "", "/" ->
                            page(
                                "<html><head></head><link rel=\"shortcut icon\" href=/favicon.png>" +
                                    "<link rel=apple-touch-icon href=/touch.png>",
                            )
                        "/touch.png" -> image(touch)
                        "/favicon.png" -> image(png())
                        else -> respondError(HttpStatusCode.NotFound)
                    }
                }

            // An unsized touch icon counts as 180 px, so it beats the unsized favicon.
            assertTrue(
                provider
                    .fetchIcon(emptyMap())
                    .getOrThrow()!!
                    .bytes
                    .contentEquals(touch),
            )
        }

    @Test
    fun takesAnInlineDataIcon() =
        runTest {
            val bytes = png()
            val provider =
                provider { page(html("""<link rel="icon" href="data:image/png;base64,${Base64.encode(bytes)}">""")) }

            assertTrue(
                provider
                    .fetchIcon(emptyMap())
                    .getOrThrow()!!
                    .bytes
                    .contentEquals(bytes),
            )
            assertEquals(1, requests.size)
        }

    @Test
    fun followsRedirectsButNeverToPlainHttp() =
        runTest {
            val provider =
                provider { request ->
                    when (request.url.encodedPath) {
                        "", "/" ->
                            respond(
                                "",
                                HttpStatusCode.Found,
                                headersOf(HttpHeaders.Location, "https://login.example.com/in"),
                            )
                        "/in" ->
                            page(
                                html(
                                    """<link rel="icon" href="http://login.example.com/insecure.png" sizes="64x64">""",
                                ),
                            )
                        else ->
                            respond(
                                "",
                                HttpStatusCode.MovedPermanently,
                                headersOf(HttpHeaders.Location, "http://login.example.com/x.ico"),
                            )
                    }
                }

            assertNull(provider.fetchIcon(emptyMap()).getOrThrow())
            assertEquals(
                listOf("dashboard.example.com/", "login.example.com/in", "login.example.com/favicon.ico"),
                paths,
            )
        }

    @Test
    fun sendsNoCredentialsAndRefusesOversizedIcons() =
        runTest {
            val provider =
                provider { request ->
                    when (request.url.encodedPath) {
                        "", "/" -> respondError(HttpStatusCode.NotFound)
                        else -> image(png(IconFormat.MAX_BYTES))
                    }
                }

            assertNull(provider.fetchIcon(emptyMap()).getOrThrow())
            requests.forEach { request ->
                assertNull(request.headers[HttpHeaders.Authorization])
                assertNull(request.headers[HttpHeaders.Cookie])
            }
        }

    @Test
    fun anUnreachableSiteIsAFailureSoItIsRetried() =
        runTest {
            val provider = provider { throw kotlinx.io.IOException("offline") }

            assertTrue(provider.fetchIcon(emptyMap()).isFailure)
        }

    @Test
    fun selfHostedAccountsUseTheirOwnServer() =
        runTest {
            val template =
                TestManifests.sites.copy(
                    hosts = listOf(SelfHostedSpec.PLACEHOLDER_HOST),
                    dashboardUrl = "https://${SelfHostedSpec.PLACEHOLDER_HOST}",
                    selfHosted = SelfHostedSpec(label = "Server"),
                )
            val provider =
                SelfHostedProvider(
                    template,
                    MapTokenVault(),
                    createPluginHttpClient(
                        MockEngine { request ->
                            recording.withLock { requests += request }
                            if (request.url.encodedPath ==
                                "/favicon.ico"
                            ) {
                                image(ico)
                            } else {
                                respondError(HttpStatusCode.NotFound)
                            }
                        },
                    ),
                )
            val settings = mapOf(SelfHostedSpec.HOST_SETTING to "deploy.home.example:8443")

            assertNull(provider.iconSite(emptyMap()))
            assertEquals("https://deploy.home.example:8443", provider.iconSite(settings))
            assertEquals(IconFormat.ICO, provider.fetchIcon(settings).getOrThrow()?.format)
            assertTrue(requests.all { it.url.host == "deploy.home.example" && it.url.port == 8443 })
        }
}
