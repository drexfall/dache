package dev.dache.plugin.runtime

import dev.dache.core.model.ProviderError
import dev.dache.plugin.api.PagingSpec
import dev.dache.plugin.api.RequestSpec
import dev.dache.plugin.runtime.http.InvalidResponseException
import dev.dache.plugin.runtime.http.PluginHttp
import dev.dache.plugin.runtime.http.nextLink
import dev.dache.plugin.runtime.mapping.Expression
import dev.dache.plugin.runtime.mapping.Template
import dev.dache.plugin.runtime.mapping.Value
import dev.dache.plugin.runtime.mapping.asText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/** Most items one fan-out sends requests for. Items past it are left out, which bounds requests per refresh. */
internal const val MAX_FAN_OUT = 100

/** Fan-out requests in flight at once, per fan-out. */
private const val MAX_CONCURRENT = 4

/** What every request for one account is sent with. */
internal class AccountContext(
    val token: String,
    val settings: Map<String, String>,
)

/** A list request with its placeholders and expressions parsed. Build only from validated specs. */
internal class CompiledList(
    request: RequestSpec,
    items: String?,
) {
    private val url = Template.parse(request.url)
    private val query = request.query.mapValues { (_, value) -> Template.parse(value) }
    private val itemsPath = items?.let(Expression::parse)
    val headers = request.headers
    val paging = request.paging
    val cursor = (paging as? PagingSpec.Cursor)?.next?.let(Expression::parse)

    /**
     * The first request's URL, placeholders filled from [item] and [settings]; null when they can't fill them.
     * A query parameter that is only an unset setting is left out.
     */
    fun url(
        item: JsonElement?,
        settings: Map<String, String>,
    ): Url? {
        val base = url.fill(item, settings, ::encodePathSegment)
        val sent = query.filterValues { value -> value.soleSetting?.let { !settings[it].isNullOrBlank() } ?: true }
        val params = sent.mapNotNull { (name, value) -> value.fill(item, settings)?.let { name to it } }
        if (base == null || params.size < sent.size) return null
        return URLBuilder(base).apply { params.forEach { (name, value) -> parameters.append(name, value) } }.build()
    }

    fun items(body: JsonElement): List<JsonElement> {
        val list = itemsPath?.evaluate(body) ?: (body as? JsonArray)?.let(Value::Items)
        return (list as? Value.Items)?.values
            ?: throw ProviderError.Unknown(InvalidResponseException("expected a list at ${itemsPath ?: "$"}"))
    }

    /** One whole path segment: `/`, `?` and `#` are encoded, and dot segments refused, so the path keeps its shape. */
    private fun encodePathSegment(value: String): String? =
        value.takeUnless { it.isEmpty() || it == "." || it == ".." }?.encodeURLParameter()
}

/** Fetches every item of a list, following its paging. */
internal class ListFetcher(
    private val http: PluginHttp,
) {
    /**
     * With [parent], the list is one branch of a fan-out: placeholders are filled from [parent], and a parent that
     * can't fill them, or whose list is gone (404), has no items rather than failing the whole refresh.
     *
     * @throws ProviderError
     */
    suspend fun fetch(
        list: CompiledList,
        account: AccountContext,
        parent: JsonElement? = null,
    ): List<JsonElement> {
        val first = list.url(parent, account.settings) ?: return emptyList()
        val token = account.token
        return try {
            when (val paging = list.paging) {
                null -> list.items(http.getJson(first, list.headers, token))
                is PagingSpec.Page -> numbered(list, paging, first, token)
                is PagingSpec.Cursor -> cursor(list, paging, first, token)
                is PagingSpec.Link -> linked(list, paging, first, token)
            }
        } catch (e: ProviderError.NotFound) {
            if (parent == null) throw e
            emptyList()
        }
    }

    /** Stops at a short page (or an empty one when the page size isn't sent), or after `maxPages`. */
    private suspend fun numbered(
        list: CompiledList,
        paging: PagingSpec.Page,
        first: Url,
        token: String,
    ): List<JsonElement> {
        val all = mutableListOf<JsonElement>()
        for (index in 0 until paging.maxPages) {
            val url = first.with(paging.sizeParams() + (paging.param to (paging.start + index).toString()))
            val page = list.items(http.getJson(url, list.headers, token))
            all += page
            val last = if (paging.sizeParam != null) page.size < paging.size else page.isEmpty()
            if (last) break
        }
        return all
    }

    private suspend fun cursor(
        list: CompiledList,
        paging: PagingSpec.Cursor,
        first: Url,
        token: String,
    ): List<JsonElement> {
        val all = mutableListOf<JsonElement>()
        val seen = mutableSetOf<String>()
        var url = first.with(paging.sizeParams())
        repeat(paging.maxPages) {
            val body = http.getJson(url, list.headers, token)
            all += list.items(body)
            val next =
                list.cursor
                    ?.evaluate(body)
                    ?.asText()
                    ?.takeIf { it.isNotEmpty() && seen.add(it) } ?: return all
            url = first.with(paging.sizeParams() + (paging.param to next))
        }
        return all
    }

    private suspend fun linked(
        list: CompiledList,
        paging: PagingSpec.Link,
        first: Url,
        token: String,
    ): List<JsonElement> {
        val all = mutableListOf<JsonElement>()
        val seen = mutableSetOf<Url>()
        var url = first.with(paging.sizeParams())
        repeat(paging.maxPages) {
            seen += url
            val response = http.get(url, list.headers, token)
            all += list.items(response.body)
            url = nextLink(response.headers[HttpHeaders.Link], url)?.takeIf { it !in seen } ?: return all
        }
        return all
    }

    private fun PagingSpec.sizeParams(): Map<String, String> = sizeParam?.let { mapOf(it to size.toString()) }.orEmpty()

    private fun Url.with(params: Map<String, String>): Url =
        URLBuilder(this).apply { params.forEach { (name, value) -> parameters[name] = value } }.build()
}

/**
 * Runs [block] for each of the first [MAX_FAN_OUT] [items], a few at a time, and returns the results in order.
 * The first failure cancels the rest.
 */
internal suspend fun <T, R> fanOut(
    items: List<T>,
    block: suspend (T) -> R,
): List<R> =
    coroutineScope {
        val permits = Semaphore(MAX_CONCURRENT)
        items.take(MAX_FAN_OUT).map { item -> async { permits.withPermit { block(item) } } }.awaitAll()
    }
