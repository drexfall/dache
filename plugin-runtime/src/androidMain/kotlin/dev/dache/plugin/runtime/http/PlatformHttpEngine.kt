package dev.dache.plugin.runtime.http

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

// The OkHttp engine disables OkHttp's own redirect following, so redirects reach PluginHttp.
internal actual fun platformHttpEngine(): HttpClientEngine = OkHttp.create()
