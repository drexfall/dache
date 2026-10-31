package dev.dache.plugin.api

import kotlinx.serialization.SerialName
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
    /** Who wrote and maintains the plugin, shown on the consent screen. */
    val publisher: String? = null,
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
    /** For software people run themselves: each account names its own server. */
    val selfHosted: SelfHostedSpec? = null,
    /** Writes the plugin offers. Without any, the plugin only reads. */
    val actions: List<ActionSpec> = emptyList(),
    /**
     * The provider's web dashboard, linked from each account and used for resources without a link of their own.
     * Required; nullable only so a manifest missing it is reported by the validator rather than the parser.
     */
    val dashboardUrl: String? = null,
    /** How hard the plugin may hit the provider. */
    val limits: LimitsSpec = LimitsSpec(),
    /** Where users create a token. */
    val tokenUrl: String? = null,
    /** Least-privilege guidance shown before the user creates a token: which permissions to grant, and why. */
    val tokenHelp: String? = null,
    /** Where editors find the JSON Schema, e.g. `../../schema/manifest.schema.json`. The engine ignores it. */
    @SerialName("\$schema")
    val schema: String? = null,
)

/**
 * A self-hosted plugin's manifest names the placeholder host [PLACEHOLDER_HOST] in `hosts` and every URL. Each
 * account gives its own server (a hostname or IPv4 address, with a port if it isn't 443), which replaces the
 * placeholder for that account only, so a token is only ever sent to the server it was entered for. [label] and
 * [hint] describe the server field.
 */
@Serializable
data class SelfHostedSpec(
    val label: String,
    val hint: String? = null,
) {
    companion object {
        const val PLACEHOLDER_HOST = "self.hosted"
        const val HOST_SETTING = "server"
        const val CERTIFICATE_SETTING = "certificate"
    }
}

/**
 * Request limits, per account. [requestsPerMinute] paces requests to the provider's documented limit, e.g. 10 on
 * UptimeRobot's free plan. [maxRequestsPerRefresh] stops a refresh that would send more; fan-outs already stop
 * at 100 requests each, so this bounds a manifest with several of them.
 */
@Serializable
data class LimitsSpec(
    val requestsPerMinute: Int? = null,
    val maxRequestsPerRefresh: Int = DEFAULT_MAX_REQUESTS_PER_REFRESH,
) {
    companion object {
        const val DEFAULT_MAX_REQUESTS_PER_REFRESH = 300
        const val MAX_REQUESTS_PER_REFRESH = 2000
        const val MAX_REQUESTS_PER_MINUTE = 6000
    }
}

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
