package dev.dache.core.model

/**
 * A write a provider offers on resources of [kind] (or, with [onDeployments], on a site's deployments).
 * [destructive] ones need the device lock on top of the confirmation screen.
 */
data class ActionInfo(
    val id: String,
    val label: String,
    val kind: ResourceKind,
    val onDeployments: Boolean,
    val destructive: Boolean,
    val description: String?,
    val inputs: List<ActionInput>,
    val permission: String?,
)

enum class ActionInputType { TEXT, NUMBER, BOOLEAN }

data class ActionInput(
    val key: String,
    val label: String,
    val type: ActionInputType,
    val required: Boolean,
    val options: List<String>,
)

/** What an action runs on: a resource, or one of a site's deployments. */
data class ActionTarget(
    val ref: ResourceRef,
    val deploymentId: String? = null,
)

data class ActionRequest(
    val actionId: String,
    val target: ActionTarget,
    val inputs: Map<String, String> = emptyMap(),
)

/** One input as it is now and as the action would set it. */
data class FieldChange(
    val label: String,
    val before: String?,
    val after: String,
)

/**
 * Exactly what running an action sends, for the confirmation screen: the method and URL (secrets redacted), the
 * body, and each input's change.
 */
data class ActionPreview(
    val action: ActionInfo,
    val method: String,
    val url: String,
    val body: String?,
    val changes: List<FieldChange>,
)
