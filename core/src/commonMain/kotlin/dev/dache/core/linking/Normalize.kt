package dev.dache.core.linking

/**
 * Reduces a URL or bare hostname to a lowercase hostname without scheme, port, path or trailing dot.
 * Returns null for blank input.
 */
fun normalizeHost(value: String): String? {
    val host =
        value
            .trim()
            .lowercase()
            .substringAfter("://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfter('@')
            .substringBefore(':')
            .trimEnd('.')
    return host.ifBlank { null }
}

/** True when [host] equals [domain] or is a subdomain of it. */
fun isWithinDomain(
    host: String,
    domain: String,
): Boolean = host == domain || host.endsWith(".$domain")

/**
 * Normalizes GitHub-style repo URLs so `git@github.com:Acme/Web.git` and `https://github.com/acme/web/` match.
 */
fun normalizeRepoUrl(value: String): String? {
    val trimmed =
        value
            .trim()
            .lowercase()
            .removeSuffix("/")
            .removeSuffix(".git")
    val withoutScheme =
        when {
            trimmed.startsWith("git@") -> trimmed.removePrefix("git@").replaceFirst(':', '/')
            "://" in trimmed -> trimmed.substringAfter("://").substringAfter('@')
            else -> trimmed
        }
    return withoutScheme.ifBlank { null }
}
