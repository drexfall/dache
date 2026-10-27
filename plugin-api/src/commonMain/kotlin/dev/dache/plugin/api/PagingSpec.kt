package dev.dache.plugin.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface PagingSpec {
    /**
     * `?<param>=<n>&<sizeParam>=<size>`, starting at [start]. Stops at a short or empty page, or after
     * [maxPages] pages so a misbehaving API can't keep the engine fetching forever.
     */
    @Serializable
    @SerialName("page")
    data class Page(
        val param: String = "page",
        val sizeParam: String? = null,
        val size: Int = 100,
        val start: Int = 1,
        val maxPages: Int = 10,
    ) : PagingSpec
}
