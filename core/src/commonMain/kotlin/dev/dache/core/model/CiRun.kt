package dev.dache.core.model

import kotlin.time.Instant

data class CiRun(
    override val providerId: ProviderId,
    override val accountId: String,
    override val nativeId: String,
    override val webUrl: String,
    val repoFullName: String,
    val repoUrl: String,
    val workflowName: String,
    val branch: String,
    val status: DeployStatus,
    val createdAt: Instant,
    val durationSeconds: Int?,
) : Resource {
    override val kind: ResourceKind get() = ResourceKind.CI_RUN
    override val displayName: String get() = "$repoFullName · $workflowName"
}
