package dev.dache.plugin.api

import kotlinx.serialization.Serializable

/**
 * A declarative provider plugin: which hosts it may call, how the token is sent, and how responses map onto
 * Dache's unified models. Manifests hold no code, so installing one can never run anything on the device.
 */
@Serializable
data class PluginManifest(
    /** Version of this schema the manifest was written against. See [ManifestJson.CURRENT_VERSION]. */
    val manifestVersion: Int,
    /** Stable lowercase id, also the TokenVault namespace. */
    val id: String,
    val name: String,
    val version: String,
    /** Exact hostnames the plugin may call over HTTPS. No wildcards. */
    val hosts: List<String>,
    val auth: AuthSpec,
    /** What the user fills in besides the token, used in requests and fields as `{@key}`. */
    val settings: List<SettingSpec> = emptyList(),
    /** Names of `Capability` values the plugin provides. */
    val capabilities: List<String>,
    val validate: ValidateSpec,
    val resources: List<ResourceSpec>,
    /** How the account's resources are split into pages. Without any, each kind and service gets its own. */
    val pages: List<PageSpec> = emptyList(),
)

/**
 * One page beside the account's dashboard, e.g. DNS. [resources] names what it shows: kind names such as
 * `dnsRecord`, and the ids of `service` resources. Every resource must be on some page.
 */
@Serializable
data class PageSpec(
    val id: String,
    val title: String,
    val resources: List<String>,
)

/**
 * A non-secret account value, such as a team slug. An optional setting left blank fills no placeholder: a query
 * parameter that is only `{@key}` is dropped, and anything else using it has no value.
 */
@Serializable
data class SettingSpec(
    val key: String,
    val label: String,
    val hint: String? = null,
    val required: Boolean = false,
)

/** The request that checks a token, and the expression that names the account it belongs to. */
@Serializable
data class ValidateSpec(
    val request: RequestSpec,
    val displayName: String,
    /** The token permission the request needs, as the provider names it. */
    val permission: String? = null,
)

/**
 * One list endpoint and how each of its items becomes a unified model of [kind].
 * [items] is the path to the array in the response; null means the response itself is the array.
 *
 * With [forEach], [request] is sent once per item of that earlier list (e.g. Pages projects per account), and
 * its `{expression}` placeholders are filled from that item.
 *
 * [permission] is the token permission the request needs, as the provider names it, so connecting can check it.
 * An [optional] resource the token can't read (403) or that doesn't exist (404) is left out instead of failing
 * the refresh; it must name its permission so the app can say what is missing.
 */
@Serializable
data class ResourceSpec(
    val kind: String,
    val request: RequestSpec,
    val items: String? = null,
    val forEach: ListSpec? = null,
    val fields: Map<String, FieldSpec>,
    /** Provider status string to `DeployStatus` name, applied to the `status` field. */
    val statusMap: Map<String, String> = emptyMap(),
    /** Required for kind `service`, and only allowed there: what the items are. */
    val service: ServiceSpec? = null,
    val permission: String? = null,
    val optional: Boolean = false,
)

/**
 * What a `service` resource's items are. [id] is unique within the manifest and names it on pages; [label] is
 * the plural shown as its section heading, e.g. "R2 buckets".
 */
@Serializable
data class ServiceSpec(
    val id: String,
    val label: String,
)

/** A list endpoint on its own: the request and the path to the array in its response, null for the response. */
@Serializable
data class ListSpec(
    val request: RequestSpec,
    val items: String? = null,
)

/**
 * A GET request. [url] path segments and [query] values may hold `{@key}` placeholders for account settings and,
 * when it is sent once per item of another list, `{expression}` placeholders evaluated against that item, e.g.
 * `/projects/{id}/deployments`. Values filled into the path are percent-encoded whole, so they can't add path
 * segments.
 */
@Serializable
data class RequestSpec(
    val url: String,
    val method: String = "GET",
    val query: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val paging: PagingSpec? = null,
)
