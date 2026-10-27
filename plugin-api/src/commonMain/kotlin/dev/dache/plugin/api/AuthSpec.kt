package dev.dache.plugin.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Where the token goes. The engine injects it at send time, into this slot and nowhere else. */
@Serializable
sealed interface AuthSpec {
    /** `Authorization: Bearer <token>`. */
    @Serializable
    @SerialName("bearer")
    data object Bearer : AuthSpec

    /** `<name>: <prefix><token>`, e.g. `X-Auth-Key`. */
    @Serializable
    @SerialName("header")
    data class Header(
        val name: String,
        val prefix: String = "",
    ) : AuthSpec

    /** `?<name>=<token>`. Only for APIs that accept nothing else, since URLs end up in more places. */
    @Serializable
    @SerialName("query")
    data class Query(
        val name: String,
    ) : AuthSpec
}
