package dev.dache.core.actions

import dev.dache.core.account.AccountRepository
import dev.dache.core.dashboard.DashboardRepository
import dev.dache.core.fakes.InMemoryAccountStore
import dev.dache.core.fakes.InMemoryTokenVault
import dev.dache.core.model.AccountInfo
import dev.dache.core.model.ActionInfo
import dev.dache.core.model.ActionRequest
import dev.dache.core.model.ActionTarget
import dev.dache.core.model.Capability
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.ResourceKind
import dev.dache.core.model.ResourceRef
import dev.dache.core.provider.DevOpsProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActionServiceTest {
    private val provider = RebootingProvider()
    private val log = InMemoryWriteLog()

    @Test
    fun eachWriteIsLoggedWithItsOutcomeAndNoInputs() =
        runTest {
            val accounts =
                AccountRepository(InMemoryAccountStore(), InMemoryTokenVault(), mapOf(provider.id to provider))
            val account = accounts.connect(provider.id, "token-123").getOrThrow()
            val service = ActionService(accounts, DashboardRepository(accounts, backgroundScope), log)
            val request =
                ActionRequest(
                    "reboot",
                    ActionTarget(ResourceRef(provider.id, account.id, "d1", ResourceKind.SERVER)),
                    mapOf("secret" to "hunter2"),
                )

            assertTrue(service.run(account, request, "web-1").isSuccess)
            provider.failing = true
            assertTrue(service.run(account, request, "web-1").isFailure)

            val (failed, ok) = log.flow.value
            assertTrue(ok.ok)
            assertEquals("Reboot", ok.action)
            assertEquals("web-1", ok.target)
            assertFalse(failed.ok)
            assertFalse(
                log.flow.value
                    .toString()
                    .contains("hunter2"),
            )
            assertFalse(
                log.flow.value
                    .toString()
                    .contains("token-123"),
            )
            assertEquals(
                listOf("reboot"),
                service.actionsFor(provider.id, ResourceKind.SERVER, onDeployments = false).map { it.id },
            )
        }
}

private class RebootingProvider : DevOpsProvider {
    var failing = false
    override val id = ProviderId.DIGITALOCEAN
    override val capabilities = setOf(Capability.SERVERS)
    override val actions =
        listOf(
            ActionInfo("reboot", "Reboot", ResourceKind.SERVER, false, null, true, null, emptyList(), "droplet:update"),
        )

    override suspend fun validateCredentials(
        token: String,
        settings: Map<String, String>,
    ) = Result.success(AccountInfo("me"))

    override suspend fun fetchSnapshot(account: ConnectedAccount) = Result.success(ProviderSnapshot())

    override suspend fun runAction(
        account: ConnectedAccount,
        request: ActionRequest,
    ): Result<Unit> = if (failing) Result.failure(ProviderError.Server(500)) else Result.success(Unit)
}

private class InMemoryWriteLog : WriteLog {
    val flow = MutableStateFlow<List<WriteLogEntry>>(emptyList())
    override val entries: Flow<List<WriteLogEntry>> = flow

    override suspend fun append(entry: WriteLogEntry) = flow.update { listOf(entry) + it }
}
