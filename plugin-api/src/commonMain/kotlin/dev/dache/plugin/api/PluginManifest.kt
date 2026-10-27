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
    /** Names of `Capability` values the plugin provides. */
    val capabilities: List<String>,
    val validate: ValidateSpec,
    val resources: List<ResourceSpec>,
)

/** The request that checks a token, and the expression that names the account it belongs to. */
@Serializable
data class ValidateSpec(
    val request: RequestSpec,
    val displayName: String,
)

/**
 * One list endpoint and how each of its items becomes a unified model of [kind].
 * [items] is the path to the array in the response; null means the response itself is the array.
 */
@Serializable
data class ResourceSpec(
    val kind: String,
    val request: RequestSpec,
    val items: String? = null,
    val fields: Map<String, FieldSpec>,
    /** Provider status string to `DeployStatus` name, applied to the `status` field. */
    val statusMap: Map<String, String> = emptyMap(),
)

@Serializable
data class RequestSpec(
    val url: String,
    val method: String = "GET",
    val query: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val paging: PagingSpec? = null,
)
