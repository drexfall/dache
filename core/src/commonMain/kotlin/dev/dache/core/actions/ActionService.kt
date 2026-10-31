package dev.dache.core.actions

import dev.dache.core.account.AccountRepository
import dev.dache.core.dashboard.DashboardRepository
import dev.dache.core.model.ActionInfo
import dev.dache.core.model.ActionPreview
import dev.dache.core.model.ActionRequest
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ResourceKind

/** Finds a provider's actions and runs them for an account, refreshing the dashboard after a write. */
class ActionService(
    private val accounts: AccountRepository,
    private val dashboard: DashboardRepository,
) {
    fun actionsFor(
        providerId: ProviderId,
        kind: ResourceKind,
        onDeployments: Boolean,
    ): List<ActionInfo> =
        accounts.catalog[providerId]
            ?.actions
            .orEmpty()
            .filter { it.kind == kind && it.onDeployments == onDeployments }

    suspend fun defaults(
        account: ConnectedAccount,
        request: ActionRequest,
    ): Result<Map<String, String>> = provider(account).mapCatching { it.actionDefaults(account, request).getOrThrow() }

    suspend fun preview(
        account: ConnectedAccount,
        request: ActionRequest,
    ): Result<ActionPreview> = provider(account).mapCatching { it.previewAction(account, request).getOrThrow() }

    /** Runs [request], then refreshes so the dashboard shows what the write changed. */
    suspend fun run(
        account: ConnectedAccount,
        request: ActionRequest,
    ): Result<Unit> =
        provider(account)
            .mapCatching { it.runAction(account, request).getOrThrow() }
            .onSuccess { dashboard.refresh() }

    private fun provider(account: ConnectedAccount) =
        accounts.catalog[account.providerId]?.let { Result.success(it) } ?: Result.failure(ProviderError.NotFound())
}
