package dev.dache.plugin.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How a list endpoint is walked. Every style stops after `maxPages` so a misbehaving API can't loop forever. */
@Serializable
sealed interface PagingSpec {
    /** Query parameter for the page size, or null to leave it to the provider. */
    val sizeParam: String?
    val size: Int
    val maxPages: Int

    /**
     * `?<param>=<n>&<sizeParam>=<size>`, starting at [start]. Stops at a short or empty page, or after
     * [maxPages] pages so a misbehaving API can't keep the engine fetching forever.
     */
    @Serializable
    @SerialName("page")
    data class Page(
        val param: String = "page",
        override val sizeParam: String? = null,
        override val size: Int = 100,
        val start: Int = 1,
        override val maxPages: Int = 10,
    ) : PagingSpec

    /**
     * The first request has no cursor; each later one sends `?<param>=<cursor>`, where the cursor is [next]
     * evaluated against the previous response. Stops when [next] is missing or empty, or repeats a cursor.
     */
    @Serializable
    @SerialName("cursor")
    data class Cursor(
        val param: String,
        val next: String,
        override val sizeParam: String? = null,
        override val size: Int = 100,
        override val maxPages: Int = 10,
    ) : PagingSpec

    /**
     * Follows the `Link: <url>; rel="next"` response header, or with [next], the URL that expression gives in the
     * body, such as UptimeRobot's `nextLink`. The next URL goes through the same host checks as every other
     * request. [sizeParam] is only sent on the first request; providers carry it in their links.
     */
    @Serializable
    @SerialName("link")
    data class Link(
        val next: String? = null,
        override val sizeParam: String? = null,
        override val size: Int = 100,
        override val maxPages: Int = 10,
    ) : PagingSpec
}
