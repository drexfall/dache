package dev.dache.core.model

/**
 * What a token can do, checked before connecting and again from an account's settings. [displayName] is the
 * account the token belongs to.
 */
data class TokenCheck(
    val displayName: String,
    val permissions: List<PermissionCheck>,
) {
    /** A required permission is known to be missing, so refreshes would fail. */
    val missingRequired: Boolean get() = permissions.any { it.permission.required && it.granted == false }
}

/**
 * A permission the token needs, named as the provider names it. Without a [required] one refreshes fail; without
 * an optional one only the pages that need it stay empty.
 */
data class TokenPermission(
    val name: String,
    val required: Boolean,
)

/** [granted] is null when it couldn't be checked, e.g. there was no zone to read DNS records from. */
data class PermissionCheck(
    val permission: TokenPermission,
    val granted: Boolean?,
)
