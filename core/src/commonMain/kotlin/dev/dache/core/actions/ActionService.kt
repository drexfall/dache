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
import kotlin.time.Clock

/**
 * Finds a provider's actions and runs them for an account, logging each write to [log] and refreshing the
 * dashboard after one succeeds.
 */
class ActionService(
    private val accounts: AccountRepository,
    private val dashboard: DashboardRepository,
    private val log: WriteLog,
    private val clock: Clock = Clock.System,
) {
    /** [service] narrows a service resource's actions to its own service. */
    fun actionsFor(
        providerId: ProviderId,
        kind: ResourceKind,
        onDeployments: Boolean,
        service: String? = null,
    ): List<ActionInfo> =
        accounts.catalog[providerId]
            ?.actions
            .orEmpty()
            .filter { it.kind == kind && it.onDeployments == onDeployments && it.service == service }

    suspend fun defaults(
        account: ConnectedAccount,
        request: ActionRequest,
    ): Result<Map<String, String>> = provider(account).mapCatching { it.actionDefaults(account, request).getOrThrow() }

    suspend fun preview(
        account: ConnectedAccount,
        request: ActionRequest,
    ): Result<ActionPreview> = provider(account).mapCatching { it.previewAction(account, request).getOrThrow() }

    /** Runs [request] on [targetName], logs it, then refreshes so the dashboard shows what the write changed. */
    suspend fun run(
        account: ConnectedAccount,
        request: ActionRequest,
        targetName: String,
    ): Result<Unit> {
        val provider = provider(account)
        val result = provider.mapCatching { it.runAction(account, request).getOrThrow() }
        log.append(
            WriteLogEntry(
                atMillis = clock.now().toEpochMilliseconds(),
                pluginId = account.providerId.id,
                accountId = account.id,
                accountName = account.displayName,
                action =
                    provider
                        .getOrNull()
                        ?.actions
                        ?.firstOrNull { it.id == request.actionId }
                        ?.label ?: request.actionId,
                target = targetName,
                ok = result.isSuccess,
                error = result.exceptionOrNull()?.let { it.message ?: it::class.simpleName },
            ),
        )
        return result.onSuccess { dashboard.refresh() }
    }

    private fun provider(account: ConnectedAccount) =
        accounts.catalog[account.providerId]?.let { Result.success(it) } ?: Result.failure(ProviderError.NotFound())
}
