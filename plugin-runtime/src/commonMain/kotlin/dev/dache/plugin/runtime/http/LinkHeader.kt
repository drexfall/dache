package dev.dache.plugin.runtime.http

import dev.dache.core.model.ProviderError
import io.ktor.http.URLParserException
import io.ktor.http.Url

private val linkValue = Regex("<([^>]*)>((?:\\s*;\\s*[^;,]+)*)")
private val relParam = Regex(";\\s*rel\\s*=\\s*(\"[^\"]*\"|[^;,\\s]+)", RegexOption.IGNORE_CASE)

/**
 * The `rel="next"` target in an RFC 8288 `Link` header (`<https://…?page=2>; rel="next", <…>; rel="last"`),
 * resolved against [base] since it may be relative. Null when there is no next link.
 *
 * @throws ProviderError.Unknown when the next link isn't a URL.
 */
internal fun nextLink(
    header: String?,
    base: Url,
): Url? {
    val target =
        header?.let {
            linkValue
                .findAll(it)
                .firstOrNull(::isNext)
                ?.groupValues
                ?.get(1)
        } ?: return null
    return resolveLink(target, base)
}

/** [target], relative to [base] or absolute, as a URL. */
internal fun resolveLink(
    target: String,
    base: Url,
): Url =
    try {
        base.resolve(target)
    } catch (e: URLParserException) {
        throw ProviderError.Unknown(InvalidResponseException("next link is not a URL: ${e::class.simpleName}"))
    }

private fun isNext(link: MatchResult): Boolean =
    relParam.findAll(link.groupValues[2]).any { rel ->
        rel.groupValues[1]
            .trim('"')
            .split(' ')
            .any { it.equals("next", ignoreCase = true) }
    }
