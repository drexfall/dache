package dev.dache.plugin.runtime.security

import io.ktor.http.URLParserException
import io.ktor.http.URLProtocol
import io.ktor.http.Url

/**
 * Allows a request only to `https://<host>` on the default port, where host exactly matches one of the
 * manifest's hosts. No wildcards, no subdomains, no credentials in the URL. Every request and every
 * redirect goes through [check] before anything is sent.
 */
class HostGuard(
    hosts: Collection<String>,
    /** Only a self-hosted server may use another port, the one its account names. */
    private val port: Int = URLProtocol.HTTPS.defaultPort,
) {
    private val allowed = hosts.map { it.lowercase() }.toSet()

    /** @throws HostNotAllowedException */
    fun check(url: Url) {
        violation(url)?.let { throw HostNotAllowedException(it) }
    }

    /** Why [url] is not allowed, or null if it is. The message never includes the path or query. */
    fun violation(url: Url): String? =
        when {
            url.protocol != URLProtocol.HTTPS -> "only https is allowed, got ${url.protocol.name}"
            url.encodedUser != null || url.encodedPassword != null -> "credentials in URLs are not allowed"
            url.port != port ->
                if (port ==
                    URLProtocol.HTTPS.defaultPort
                ) {
                    "only the default https port is allowed"
                } else {
                    "only port $port is allowed"
                }
            url.host.lowercase() !in allowed -> "host ${url.host} is not declared in the manifest"
            else -> null
        }

    fun violation(url: String): String? {
        val parsed = parseUrl(url) ?: return "not a valid URL"
        return violation(parsed)
    }

    companion object {
        /** Lowercase DNS name with at least one dot. No scheme, port, path or wildcard. */
        private val hostPattern = Regex("([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}")

        fun isValidHost(host: String): Boolean = hostPattern.matches(host)

        private val ipv4Pattern =
            Regex("((25[0-5]|2[0-4][0-9]|1?[0-9]?[0-9])\\.){3}(25[0-5]|2[0-4][0-9]|1?[0-9]?[0-9])")

        /** A self-hosted server: a hostname (a LAN name without a dot too) or an IPv4 address. */
        fun isValidServerHost(host: String): Boolean =
            hostPattern.matches(host) ||
                ipv4Pattern.matches(host) ||
                Regex("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?").matches(host)

        fun parseUrl(url: String): Url? =
            try {
                Url(url).takeIf { url.contains("://") }
            } catch (_: URLParserException) {
                null
            }
    }
}

class HostNotAllowedException(
    message: String,
) : Exception(message)
