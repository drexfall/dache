package dev.dache.core.model

import kotlin.time.Instant

/** A deployable site or project (Netlify site, Vercel project, Cloudflare Pages project, DO app). */
data class Site(
    override val providerId: ProviderId,
    override val accountId: String,
    override val nativeId: String,
    override val webUrl: String,
    val name: String,
    val primaryUrl: String,
    val customDomains: List<String>,
    val repoUrl: String?,
    /** Recent deploy history, any order. */
    val deployments: List<Deployment>,
) : Resource {
    override val kind: ResourceKind get() = ResourceKind.SITE
    override val displayName: String get() = name

    val latestDeployment: Deployment? get() = deployments.maxByOrNull { it.createdAt }
}

data class Deployment(
    val nativeId: String,
    val webUrl: String,
    val status: DeployStatus,
    val branch: String?,
    val commitMessage: String?,
    val createdAt: Instant,
    /** Build time; null while building or when the provider doesn't report it. */
    val durationSeconds: Int?,
)
