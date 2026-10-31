package dev.dache.core.alerts

import dev.dache.core.account.AccountRepository
import dev.dache.core.dashboard.DashboardRepository
import dev.dache.core.fakes.InMemoryAccountStore
import dev.dache.core.fakes.InMemoryTokenVault
import dev.dache.core.model.AccountInfo
import dev.dache.core.model.Capability
import dev.dache.core.model.ConnectedAccount
import dev.dache.core.model.Monitor
import dev.dache.core.model.MonitorStatus
import dev.dache.core.model.ProviderError
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.provider.DevOpsProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackgroundCheckTest {
    private val provider = MonitorsProvider()
    private val store = InMemoryAlertStore()

    private suspend fun check(scope: kotlinx.coroutines.CoroutineScope): BackgroundCheck {
        val vault = InMemoryTokenVault()
        val accounts = AccountRepository(InMemoryAccountStore(), vault, mapOf(provider.id to provider))
        accounts.connect(provider.id, "token-123")
        return BackgroundCheck(DashboardRepository(accounts, scope), store)
    }

    @Test
    fun theFirstCheckRecordsWhatIsAlreadyDownWithoutAlerting() =
        runTest {
            provider.down = setOf("api")
            val check = check(backgroundScope)

            assertTrue(check.run().isEmpty())
            assertEquals(1, store.reported?.size)
        }

    @Test
    fun aMonitorGoingDownAlertsOnceAndAgainAfterItRecovers() =
        runTest {
            val check = check(backgroundScope)
            check.run()

            provider.down = setOf("api")
            assertEquals(listOf("Down: api"), check.run().map { it.title })
            assertTrue(check.run().isEmpty())

            provider.down = emptySet()
            check.run()
            provider.down = setOf("api")
            assertEquals(1, check.run().size)
        }

    @Test
    fun aFailedRefreshForgetsNothing() =
        runTest {
            val check = check(backgroundScope)
            provider.down = setOf("api")
            check.run()
            check.run()

            provider.failing = true
            assertTrue(check.run().isEmpty())
            provider.failing = false
            assertTrue(check.run().isEmpty(), "the monitor was down all along; it must not alert again")
        }

    @Test
    fun turnedOffKindsDontAlert() =
        runTest {
            store.settings.value = AlertSettings(enabled = true, monitors = false)
            val check = check(backgroundScope)
            check.run()

            provider.down = setOf("api")
            assertTrue(check.run().isEmpty())
        }
}

private class MonitorsProvider : DevOpsProvider {
    var down = emptySet<String>()
    var failing = false
    override val id = ProviderId.UPTIMEROBOT
    override val capabilities = setOf(Capability.MONITORS)

    override suspend fun validateCredentials(
        token: String,
        settings: Map<String, String>,
    ) = Result.success(AccountInfo("me"))

    override suspend fun fetchSnapshot(account: ConnectedAccount): Result<ProviderSnapshot> =
        if (failing) {
            Result.failure(ProviderError.Network())
        } else {
            Result.success(
                ProviderSnapshot(
                    monitors =
                        listOf("api", "web").map { name ->
                            Monitor(
                                providerId = id,
                                accountId = account.id,
                                nativeId = name,
                                webUrl = "https://example.com",
                                name = name,
                                url = "https://$name.example.com",
                                status = if (name in down) MonitorStatus.DOWN else MonitorStatus.UP,
                                uptimePercent = null,
                                responseTimeMs = null,
                            )
                        },
                ),
            )
        }
}

private class InMemoryAlertStore : AlertStore {
    val settings = MutableStateFlow(AlertSettings(enabled = true))
    var reported: Set<String>? = null
    override val alertSettings: Flow<AlertSettings> = settings

    override suspend fun setAlertSettings(settings: AlertSettings) {
        this.settings.value = settings
    }

    override suspend fun reportedAlerts(): Set<String>? = reported

    override suspend fun setReportedAlerts(keys: Set<String>) {
        reported = keys
    }
}
