package dev.dache.plugin.runtime

import dev.dache.core.model.IconFormat
import dev.dache.core.model.ProviderIcon
import dev.dache.plugin.api.SelfHostedSpec
import dev.dache.plugin.runtime.http.resolve
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.URLParserException
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray
import kotlin.math.abs

/**
 * Finds a site's favicon: the icons its page links to, best first, then `/favicon.ico`. Requests carry no token
 * and no cookie, go to https only, follow at most [MAX_REDIRECTS] redirects, and read a capped number of bytes;
 * what comes back is used only if its first bytes say it's an image. The plugin's HostGuard doesn't apply: a
 * site's icon often lives on another host (a CDN), and nothing secret is sent.
 */
internal class Favicons(
    private val client: HttpClient,
) {
    /** [page]'s icon, or null when it has none the app can use. Throws when the site can't be reached at all. */
    suspend fun fetch(page: Url): ProviderIcon? {
        val response = get(page, MAX_PAGE_BYTES, accept = "text/html")
        val html = response?.takeIf { it.ok && "html" in it.contentType }?.body?.decodeToString()
        val base = response?.url ?: page
        val linked = html?.let { iconLinks(it, base) }.orEmpty()
        val fallback = listOfNotNull(base.resolveHttps("/favicon.ico")?.let(Candidate::Remote))
        return (linked + fallback).distinct().firstNotNullOfOrNull { candidate ->
            when (candidate) {
                is Candidate.Inline -> ProviderIcon.fromDataUri(candidate.uri)
                is Candidate.Remote -> image(candidate.url)
            }
        }
    }

    // One bad icon link shouldn't stop the next from being tried.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun image(url: Url): ProviderIcon? =
        try {
            get(url, IconFormat.MAX_BYTES, accept = "image/*")
                ?.takeIf { it.ok && it.body.size <= IconFormat.MAX_BYTES }
                ?.let { ProviderIcon.of(it.body) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    /** What [start] answered after redirects, its body read up to one byte past [limit]; null if it left https. */
    private suspend fun get(
        start: Url,
        limit: Int,
        accept: String,
    ): Fetched? {
        var next: Url? = start.takeIf { it.protocol == URLProtocol.HTTPS }
        var hops = 0
        var answer: Fetched? = null
        while (next != null && answer == null && hops++ <= MAX_REDIRECTS) {
            val fetched = once(next, limit, accept)
            if (fetched.redirect == null) answer = fetched else next = next.resolveHttps(fetched.redirect)
        }
        return answer
    }

    private suspend fun once(
        url: Url,
        limit: Int,
        accept: String,
    ): Fetched =
        client.prepareGet(url) { header(HttpHeaders.Accept, accept) }.execute { response ->
            val location = response.headers[HttpHeaders.Location]
            if (response.status.value in REDIRECTS && location != null) {
                Fetched(url, ok = false, contentType = "", body = ByteArray(0), redirect = location)
            } else {
                Fetched(
                    url = url,
                    ok = response.status.isSuccess(),
                    contentType = response.headers[HttpHeaders.ContentType].orEmpty().lowercase(),
                    body = response.bodyAsChannel().readRemaining(limit + 1L).readByteArray(),
                )
            }
        }

    private class Fetched(
        val url: Url,
        val ok: Boolean,
        val contentType: String,
        val body: ByteArray,
        val redirect: String? = null,
    )

    private sealed interface Candidate {
        data class Remote(
            val url: Url,
        ) : Candidate

        data class Inline(
            val uri: String,
        ) : Candidate
    }

    /**
     * The `<link rel="…icon…">` targets in [html], best first: a raster icon of at least [GOOD] px, nearest
     * [ENOUGH] px; then SVG, which not every platform can draw; then small or unsized ones. The whole page is
     * searched, not just its head: some apps (DigitalOcean's) render their head tags after `</head>`.
     */
    private fun iconLinks(
        html: String,
        base: Url,
    ): List<Candidate> =
        linkTag
            .findAll(html)
            .map { attributes(it.value) }
            .filter { attrs ->
                attrs["rel"]
                    .orEmpty()
                    .lowercase()
                    .split(' ')
                    .any { it in iconRels }
            }.mapNotNull { attrs -> attrs["href"]?.trim()?.takeIf { it.isNotEmpty() }?.let { it to score(attrs) } }
            .sortedByDescending { it.second }
            .mapNotNull { (href, _) ->
                if (href.startsWith("data:", ignoreCase = true)) {
                    Candidate.Inline(href)
                } else {
                    base.resolveHttps(href)?.let(Candidate::Remote)
                }
            }.toList()

    private fun score(attrs: Map<String, String>): Int {
        val type = attrs["type"].orEmpty().lowercase()
        val path = attrs["href"].orEmpty().lowercase().substringBefore('?')
        val sizes = attrs["sizes"].orEmpty().lowercase()
        val largest = sizes.split(' ').mapNotNull { it.substringBefore('x').toIntOrNull() }.maxOrNull()
        val rel = attrs["rel"].orEmpty().lowercase()
        // Unsized touch icons are 180 px by Apple's convention; unsized .ico files are usually 16.
        val size =
            largest ?: when {
                "apple-touch-icon" in rel -> TOUCH_ICON_SIZE
                path.endsWith(".ico") -> ICO_SIZE
                else -> UNKNOWN_SIZE
            }
        return when {
            "svg" in type || path.endsWith(".svg") || sizes == "any" -> SVG_SCORE
            size < GOOD -> size
            // Past what an avatar needs, bigger only costs bytes.
            else -> RASTER_SCORE - abs(size - ENOUGH)
        }
    }

    private fun attributes(tag: String): Map<String, String> =
        attribute.findAll(tag).associate { match ->
            val value =
                match.groupValues
                    .drop(2)
                    .firstOrNull { it.isNotEmpty() }
                    .orEmpty()
            match.groupValues[1].lowercase() to decodeEntities(value)
        }

    private fun decodeEntities(text: String): String =
        text
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")

    // A link that isn't a URL is simply not a candidate.
    @Suppress("SwallowedException")
    private fun Url.resolveHttps(target: String): Url? =
        try {
            resolve(target).takeIf { it.protocol == URLProtocol.HTTPS }
        } catch (e: URLParserException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }

    companion object {
        private const val MAX_REDIRECTS = 4
        private const val MAX_PAGE_BYTES = 256 * 1024
        private const val RASTER_SCORE = 10_000
        private const val SVG_SCORE = 1_000
        private const val GOOD = 32
        private const val ICO_SIZE = 16
        private const val TOUCH_ICON_SIZE = 180
        private const val UNKNOWN_SIZE = 32
        private const val ENOUGH = 192
        private val REDIRECTS = setOf(301, 302, 303, 307, 308)
        private val iconRels = setOf("icon", "apple-touch-icon", "apple-touch-icon-precomposed")
        private val linkTag = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
        private val attribute = Regex("([a-zA-Z-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))")
    }
}

/** The page to look for a favicon on: the manifest's dashboard, unless it's the self-hosted placeholder. */
internal fun iconPageOf(dashboardUrl: String?): Url? =
    dashboardUrl
        ?.let { runCatching { Url(it) }.getOrNull() }
        ?.takeIf {
            it.protocol == URLProtocol.HTTPS &&
                it.host.isNotEmpty() &&
                it.host != SelfHostedSpec.PLACEHOLDER_HOST
        }

/** [page]'s site, `https://host` or `https://host:port`, the key its icon is cached under. */
internal fun siteOf(page: Url): String =
    if (page.port == URLProtocol.HTTPS.defaultPort) "https://${page.host}" else "https://${page.host}:${page.port}"
