package dev.dache.plugin.registry

import dev.dache.core.model.ProviderId
import dev.dache.core.provider.DevOpsProvider
import dev.dache.core.security.TokenVault
import dev.dache.plugin.api.PluginManifest
import dev.dache.plugin.runtime.LoadResult
import dev.dache.plugin.runtime.ManifestLoader
import dev.dache.plugin.runtime.ManifestProvider
import dev.dache.plugin.runtime.RequestLogger
import io.ktor.client.HttpClient

/** Plugins that ship inside the app. Their manifests live in `plugin-registry/manifests`. */
object BundledPlugins {
    /** Every bundled manifest. One that fails validation is a build bug, so this throws instead of skipping it. */
    val manifests: List<PluginManifest> by lazy {
        bundledManifestJson.map { (name, json) ->
            when (val result = ManifestLoader.load(json)) {
                is LoadResult.Loaded -> result.manifest
                is LoadResult.Invalid -> error("Bundled manifest $name is invalid: ${result.problems}")
            }
        }
    }

    /** A provider for each bundled manifest. */
    fun providers(
        vault: TokenVault,
        client: HttpClient,
        logger: RequestLogger = RequestLogger.None,
    ): Map<ProviderId, DevOpsProvider> =
        manifests.associate { manifest ->
            val provider = ManifestProvider(manifest, vault = vault, client = client, logger = logger)
            provider.id to provider
        }
}
