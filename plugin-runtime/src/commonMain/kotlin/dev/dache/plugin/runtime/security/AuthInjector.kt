package dev.dache.plugin.runtime.security

import dev.dache.core.model.ProviderError
import dev.dache.plugin.api.AuthSpec
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders

/**
 * Writes the secret into the manifest's declared slot on an outgoing request, right before it is sent.
 * Requests are built without the secret, so nothing upstream (URL building, logging) can ever see it.
 */
internal object AuthInjector {
    /** @throws ProviderError.Unauthorized if [secret] contains control characters, e.g. a pasted newline. */
    fun inject(
        request: HttpRequestBuilder,
        auth: AuthSpec,
        secret: String,
    ) {
        if (secret.isEmpty() || secret.any { it.isISOControl() }) throw ProviderError.Unauthorized()
        when (auth) {
            AuthSpec.Bearer -> request.headers[HttpHeaders.Authorization] = "Bearer $secret"
            is AuthSpec.Header -> request.headers[auth.name] = auth.prefix + secret
            is AuthSpec.Query -> request.url.parameters[auth.name] = secret
        }
    }
}
