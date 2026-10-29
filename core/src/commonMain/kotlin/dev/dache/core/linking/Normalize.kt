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
 * True when the worker route [pattern] answers requests for [host]. A pattern is a hostname and a path, and a
 * leading `*` in the hostname stands for any prefix: `*.example.com` covers `api.example.com` but not
 * `example.com`, and `*example.com` covers both.
 */
fun routeCovers(
    pattern: String,
    host: String,
): Boolean {
    val route = normalizeHost(pattern) ?: return false
    return when {
        route.startsWith("*.") -> host.endsWith(route.removePrefix("*"))
        route.startsWith("*") -> route.length > 1 && isWithinDomain(host, route.removePrefix("*"))
        else -> host == route
    }
}

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
