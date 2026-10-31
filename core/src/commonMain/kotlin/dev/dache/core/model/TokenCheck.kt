package dev.dache.core.model

/**
 * What a token can do, checked before connecting and again from an account's settings. [displayName] is the
 * account the token belongs to, or null when the provider refused the token outright (a 403 on the request that
 * identifies the account); its permissions then say which one that request needed and leave the rest unchecked.
 */
data class TokenCheck(
    val displayName: String?,
    val permissions: List<PermissionCheck>,
) {
    val accepted: Boolean get() = displayName != null

    /** A required permission is known to be missing, so refreshes would fail. */
    val missingRequired: Boolean get() = permissions.any { it.permission.required && it.granted == false }

    /** Refreshes would work with this token. */
    val usable: Boolean get() = accepted && !missingRequired
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
