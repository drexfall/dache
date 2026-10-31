package dev.dache.plugin.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A write the plugin offers on one kind of resource, such as redeploying a site. [request]'s method is POST, PUT,
 * PATCH or DELETE; only a request that matches a declared action may use a method other than GET.
 *
 * [target] is a kind name (`site`, `ciRun`, `dnsRecord`, `server`, `monitor`, ...) or `deployment` for one of a
 * site's deployments. [request]'s URL and query, and the string values of [body], hold placeholders: `{expression}`
 * reads the target as the provider listed it (with `^` for its parent, as in fan-outs), `{@key}` an account setting,
 * and `{input.key}` what the user entered for one of [inputs]. A body value that is only `{input.key}` for a
 * number or boolean input is sent as that type.
 *
 * [destructive] actions (deleting, power-cycling, rolling back production) ask for the device's lock on top of the
 * confirmation screen. [permission] names what the token needs, as the provider names it.
 */
@Serializable
data class ActionSpec(
    val id: String,
    val label: String,
    val target: String,
    val request: RequestSpec,
    val body: JsonObject? = null,
    val inputs: List<InputSpec> = emptyList(),
    val destructive: Boolean = false,
    val description: String? = null,
    val permission: String? = null,
)

/**
 * Something the user fills in before an action runs. [default] is an expression read from the target, so an edit
 * starts from the current value and the confirmation screen can show what changes.
 */
@Serializable
data class InputSpec(
    val key: String,
    val label: String,
    val type: InputType = InputType.TEXT,
    val default: String? = null,
    val required: Boolean = true,
    val options: List<String> = emptyList(),
)

@Serializable
enum class InputType {
    @SerialName("text")
    TEXT,

    @SerialName("number")
    NUMBER,

    @SerialName("boolean")
    BOOLEAN,
}
