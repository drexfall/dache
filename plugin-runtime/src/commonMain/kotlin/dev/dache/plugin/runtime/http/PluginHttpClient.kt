package dev.dache.plugin.runtime.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout

internal expect fun platformHttpEngine(): HttpClientEngine

private const val REQUEST_TIMEOUT_MS = 30_000L
private const val CONNECT_TIMEOUT_MS = 10_000L

/**
 * The client every plugin shares. It never follows redirects itself: PluginHttp does, so each hop goes through
 * HostGuard. No cookie storage is installed, so nothing persists between requests.
 */
fun createPluginHttpClient(engine: HttpClientEngine = platformHttpEngine()): HttpClient =
    HttpClient(engine) {
        expectSuccess = false
        followRedirects = false
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
        }
    }
