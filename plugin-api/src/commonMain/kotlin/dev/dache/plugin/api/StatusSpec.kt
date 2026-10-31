package dev.dache.plugin.api

import kotlinx.serialization.Serializable

/**
 * The provider's public status page: overall health, components (products, regions, data centres) and active
 * incidents. [url] is fetched without the token, from its own host only. Expressions read its response the way
 * resource fields read theirs; [levelMap] turns the provider's words into `StatusLevel` names.
 *
 * Most providers run Atlassian Statuspage, whose `/api/v2/summary.json` maps with `status.indicator`,
 * `components` and `incidents`.
 */
@Serializable
data class StatusSpec(
    val url: String,
    /** The human status page, linked from the app. */
    val pageUrl: String? = null,
    val indicator: String,
    val description: String? = null,
    val levelMap: Map<String, String>,
    val components: StatusListSpec? = null,
    val incidents: StatusListSpec? = null,
)

/**
 * A list in the status response. Component fields: `id`, `name`, `status`, and `parent`, an expression for the id
 * of the group a component belongs to (a region holding data centres, say); components that are some other
 * component's parent become groups. Incident fields: `name`, `status`, `impact`, `updatedAt`, `url`. [statusMap]
 * maps `status` (components) or `impact` (incidents) to `StatusLevel` names.
 */
@Serializable
data class StatusListSpec(
    val items: String,
    val fields: Map<String, String>,
    val statusMap: Map<String, String> = emptyMap(),
)
