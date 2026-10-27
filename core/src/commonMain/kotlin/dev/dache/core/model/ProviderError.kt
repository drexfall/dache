package dev.dache.core.model

import kotlin.time.Duration

/** Every provider failure is mapped to one of these so the UI can show a specific message. */
sealed class ProviderError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Unauthorized : ProviderError("Unauthorized")

    class MissingScope(
        val scope: String?,
    ) : ProviderError("Missing scope")

    class RateLimited(
        val retryAfter: Duration?,
    ) : ProviderError("Rate limited")

    class NotFound : ProviderError("Not found")

    class Network : ProviderError("Network error")

    class Server(
        val code: Int,
    ) : ProviderError("Server error")

    class Unknown(
        cause: Throwable? = null,
    ) : ProviderError("Unknown error", cause)
}
